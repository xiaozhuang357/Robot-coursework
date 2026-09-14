package com.lineguard;

import com.lineguard.service.DemoDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LineGuardApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DemoDataService demoDataService;

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
}
