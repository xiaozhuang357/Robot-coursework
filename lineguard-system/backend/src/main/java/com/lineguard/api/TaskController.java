package com.lineguard.api;

import com.lineguard.api.ApiDtos.CommandRequest;
import com.lineguard.api.ApiDtos.CreateTaskRequest;
import com.lineguard.api.ApiDtos.OperationResponse;
import com.lineguard.api.ApiDtos.ReviewTaskRequest;
import com.lineguard.api.ApiDtos.TaskDetailResponse;
import com.lineguard.api.ApiDtos.TaskSummaryResponse;
import com.lineguard.service.MissionApplicationService;
import com.lineguard.service.MissionQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final MissionApplicationService applicationService;
    private final MissionQueryService queryService;

    public TaskController(MissionApplicationService applicationService, MissionQueryService queryService) {
        this.applicationService = applicationService;
        this.queryService = queryService;
    }

    @GetMapping
    public List<TaskSummaryResponse> tasks() {
        return queryService.tasks();
    }

    @GetMapping("/active")
    public ResponseEntity<TaskDetailResponse> activeTask() {
        return queryService.activeTask().map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{taskId}")
    public TaskDetailResponse task(@PathVariable UUID taskId) {
        return queryService.task(taskId);
    }

    @PostMapping
    public ResponseEntity<TaskDetailResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        UUID id = applicationService.create(request).getId();
        return ResponseEntity.status(HttpStatus.CREATED).body(queryService.task(id));
    }

    @PostMapping("/{taskId}/dispatch")
    public OperationResponse dispatch(@PathVariable UUID taskId, @Valid @RequestBody CommandRequest request) {
        applicationService.dispatch(taskId, request);
        return new OperationResponse("任务派发指令已处理", queryService.task(taskId));
    }

    @PostMapping("/{taskId}/review")
    public OperationResponse review(@PathVariable UUID taskId, @Valid @RequestBody ReviewTaskRequest request) {
        applicationService.reviewAndReturn(taskId, request);
        return new OperationResponse("复核结论已保存，Robot 开始返航", queryService.task(taskId));
    }

    @PostMapping("/{taskId}/actions/recapture")
    public OperationResponse recapture(@PathVariable UUID taskId, @Valid @RequestBody CommandRequest request) {
        applicationService.recapture(taskId, request);
        return new OperationResponse("重新采集指令已处理", queryService.task(taskId));
    }

    @PostMapping("/{taskId}/actions/return")
    public OperationResponse requestReturn(@PathVariable UUID taskId, @Valid @RequestBody CommandRequest request) {
        applicationService.requestReturn(taskId, request);
        return new OperationResponse("返航指令已处理", queryService.task(taskId));
    }

    @PostMapping("/{taskId}/actions/abort")
    public OperationResponse abort(@PathVariable UUID taskId, @Valid @RequestBody CommandRequest request) {
        applicationService.abort(taskId, request);
        return new OperationResponse("任务终止指令已处理", queryService.task(taskId));
    }
}

