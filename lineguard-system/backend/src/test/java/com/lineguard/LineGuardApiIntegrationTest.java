package com.lineguard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lineguard.api.ApiDtos.CreateTaskRequest;
import com.lineguard.domain.DomainTypes.TaskSource;
import com.lineguard.domain.InspectionTask;
import com.lineguard.repository.InspectionTaskRepository;
import com.lineguard.service.DemoDataService;
import com.lineguard.service.MissionQueryService;
import com.lineguard.service.MissionStateService;
import com.lineguard.storage.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LineGuardApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DemoDataService demoDataService;
    @Autowired
    private InspectionTaskRepository taskRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MissionStateService missionStateService;
    @Autowired
    private MissionQueryService queryService;
    @Autowired
    private StorageProperties storageProperties;

    @BeforeEach
    void resetDemo() {
        demoDataService.reset();
    }

    @Test
    void exposesSeedAssetsAndCreatesTask() throws Exception {
        mockMvc.perform(get("/api/v1/assets/segments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lengthMeters").value(1000))
                .andExpect(jsonPath("$[0].inspectionPoints.length()").value(2));

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "MANUAL",
                                  "inspectionPointId": "LG-POINT-350",
                                  "inspectionItem": "连接金具外观检查"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.task.status").value("CREATED"))
                .andExpect(jsonPath("$.inspectionPoint.positionMeters").value(350));
    }

    @Test
    void eventStreamOpensWithTheConnectedFrame() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/events/stream"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // 前端就靠这两个事件名与这一帧格式工作（见 src/stores/lineguard.ts 里的
        // addEventListener('connected') 与 'lineguard-update'）。接口这一层以前没有用例，
        // 而 SSE 保活也落在同一条路径上。
        assertThat(result.getResponse().getContentType()).contains("text/event-stream");
        String body = result.getResponse().getContentAsString();
        assertThat(body).startsWith("event:connected");
        assertThat(body).contains("\"type\":\"CONNECTED\"");
    }

    @Test
    void eventStreamPushesDeviceSnapshotsToSubscribers() throws Exception {
        MvcResult stream = mockMvc.perform(get("/api/v1/events/stream"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String taskId = objectMapper.readTree(mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source": "MANUAL", "inspectionPointId": "LG-POINT-350",
                                 "inspectionItem": "连接金具外观检查"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString())
                .path("task").path("id").asText();

        mockMvc.perform(post("/api/v1/tasks/{taskId}/dispatch", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\": \"stream-dispatch\"}"))
                .andExpect(status().isOk());

        // 订阅者要真的收到推送，而不是只有连接帧——这是整个实时界面的基础
        String pushed = stream.getResponse().getContentAsString();
        assertThat(pushed).contains("event:lineguard-update");
        assertThat(pushed).contains("\"type\":\"DEVICES_UPDATED\"");
    }

    @Test
    void servesTheInspectionImageOfADetection() throws Exception {
        UUID detectionId = runTaskUntilDetection();

        // 复核界面就是靠这个接口显示图像的。生成那张图的代码以前因为一个格式串问题
        // 静默失败过，所以这里除了状态码，还要确认拿到的确实是 SVG 内容。
        mockMvc.perform(get("/api/v1/detections/{detectionId}/image", detectionId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
                .andExpect(content().string(containsString("<svg")));
    }

    @Test
    void missingImageFileIsReportedAsNotFoundInsteadOfBadRequest() throws Exception {
        UUID detectionId = runTaskUntilDetection();
        String fileName = queryService.detection(detectionId).getImageFileName();
        Files.delete(Path.of(storageProperties.getImageRoot()).resolve(fileName));

        // 请求本身没问题、是服务端存的图像不见了：要报 404 并带上真实原因，
        // 不能压成 400"请求参数不合法"，那会让人去查参数。
        mockMvc.perform(get("/api/v1/detections/{detectionId}/image", detectionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString("检测图像不存在")));
    }

    @Test
    void unknownDetectionIsReportedAsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/detections/{detectionId}/image", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString("检测记录不存在")));
    }

    private UUID runTaskUntilDetection() {
        InspectionTask task = missionStateService.createTask(new CreateTaskRequest(
                TaskSource.MANUAL, "LG-POINT-350", "连接金具外观检查", null));
        missionStateService.dispatch(task.getId(), "image-dispatch");
        for (int tick = 0; tick < 10; tick++) {
            missionStateService.advanceSimulation(task.getId());
            var detection = queryService.task(task.getId()).latestDetection();
            if (detection != null) {
                return detection.id();
            }
        }
        throw new IllegalStateException("推进 10 次仍未产生检测记录");
    }

    @Test
    void taskListResolvesEveryPointName() throws Exception {
        for (String pointId : new String[]{"LG-POINT-350", "LG-POINT-720"}) {
            mockMvc.perform(post("/api/v1/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"source": "MANUAL", "inspectionPointId": "%s",
                                     "inspectionItem": "地线表面与异物检查"}
                                    """.formatted(pointId)))
                    .andExpect(status().isCreated());
        }

        // 批量取检测点后，每条任务的名称都要解析出来，不能退化成只认第一条
        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].inspectionPointName").isNotEmpty())
                .andExpect(jsonPath("$[1].inspectionPointName").isNotEmpty());
    }

    @Test
    void danglingInspectionPointReportsConflictInsteadOfInternalError() throws Exception {
        InspectionTask orphan = taskRepository.save(new InspectionTask(
                UUID.randomUUID(), "LG-TEST-ORPHAN", TaskSource.MANUAL, "LG-POINT-MISSING",
                "连接金具外观检查", Instant.now().plusSeconds(600)));

        // 检测点不存在属于数据不一致：要报 409 并说明原因，不能以 500 冒出来让人以为后端崩了
        mockMvc.perform(get("/api/v1/tasks/{taskId}", orphan.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").isNotEmpty());

        // 列表接口对这种数据要能容忍，不能因为一条悬空引用就把整个列表打挂
        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk());
    }
}
