package com.lineguard.service;

import com.lineguard.api.BusinessException;
import com.lineguard.domain.DomainTypes.TaskStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class TaskStateMachine {

    private final Map<TaskStatus, Set<TaskStatus>> transitions = new EnumMap<>(TaskStatus.class);

    public TaskStateMachine() {
        allow(TaskStatus.CREATED, TaskStatus.PRECHECKING, TaskStatus.ABORTED);
        allow(TaskStatus.PRECHECK_FAILED, TaskStatus.PRECHECKING, TaskStatus.ABORTED);
        allow(TaskStatus.PRECHECKING, TaskStatus.DEPARTING, TaskStatus.PRECHECK_FAILED, TaskStatus.ABORTED);
        allow(TaskStatus.DEPARTING, TaskStatus.MOVING, TaskStatus.RETURNING,
                TaskStatus.WAITING_MANUAL, TaskStatus.SAFE_PARKED, TaskStatus.ABORTED);
        allow(TaskStatus.MOVING, TaskStatus.INSPECTING, TaskStatus.RETURNING,
                TaskStatus.WAITING_MANUAL, TaskStatus.SAFE_PARKED, TaskStatus.ABORTED);
        allow(TaskStatus.INSPECTING, TaskStatus.UPLOADING, TaskStatus.RETURNING,
                TaskStatus.WAITING_MANUAL, TaskStatus.SAFE_PARKED, TaskStatus.ABORTED);
        allow(TaskStatus.UPLOADING, TaskStatus.AWAITING_REVIEW, TaskStatus.RETURNING,
                TaskStatus.WAITING_MANUAL, TaskStatus.SAFE_PARKED, TaskStatus.ABORTED);
        allow(TaskStatus.AWAITING_REVIEW, TaskStatus.INSPECTING, TaskStatus.RETURNING,
                TaskStatus.WAITING_MANUAL, TaskStatus.SAFE_PARKED, TaskStatus.ABORTED);
        allow(TaskStatus.RETURNING, TaskStatus.DOCKING, TaskStatus.WAITING_MANUAL,
                TaskStatus.SAFE_PARKED, TaskStatus.ABORTED);
        allow(TaskStatus.DOCKING, TaskStatus.COMPLETED, TaskStatus.WAITING_MANUAL,
                TaskStatus.SAFE_PARKED, TaskStatus.ABORTED);
        allow(TaskStatus.WAITING_MANUAL, TaskStatus.RETURNING, TaskStatus.SAFE_PARKED, TaskStatus.ABORTED);
        allow(TaskStatus.SAFE_PARKED, TaskStatus.RETURNING, TaskStatus.WAITING_MANUAL, TaskStatus.ABORTED);
    }

    public void check(TaskStatus from, TaskStatus to) {
        if (!transitions.getOrDefault(from, Set.of()).contains(to)) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "任务状态不能从 " + from + " 转换为 " + to);
        }
    }

    private void allow(TaskStatus from, TaskStatus... to) {
        transitions.put(from, EnumSet.copyOf(java.util.List.of(to)));
    }
}

