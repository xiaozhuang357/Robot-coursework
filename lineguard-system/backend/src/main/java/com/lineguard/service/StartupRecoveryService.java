package com.lineguard.service;

import com.lineguard.domain.Device;
import com.lineguard.domain.DomainTypes.RobotState;
import com.lineguard.domain.DomainTypes.TaskStatus;
import com.lineguard.domain.InspectionTask;
import com.lineguard.domain.TaskEvent;
import com.lineguard.repository.DeviceRepository;
import com.lineguard.repository.InspectionTaskRepository;
import com.lineguard.repository.TaskEventRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Order(2)
public class StartupRecoveryService implements ApplicationRunner {

    private final InspectionTaskRepository taskRepository;
    private final DeviceRepository deviceRepository;
    private final TaskEventRepository eventRepository;

    public StartupRecoveryService(InspectionTaskRepository taskRepository,
                                  DeviceRepository deviceRepository,
                                  TaskEventRepository eventRepository) {
        this.taskRepository = taskRepository;
        this.deviceRepository = deviceRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<InspectionTask> interrupted = taskRepository.findAll().stream()
                .filter(task -> TaskStatus.activeStatuses().contains(task.getStatus()))
                .filter(task -> task.getStatus() != TaskStatus.WAITING_MANUAL
                        && task.getStatus() != TaskStatus.SAFE_PARKED
                        && task.getStatus() != TaskStatus.AWAITING_REVIEW)
                .toList();
        if (interrupted.isEmpty()) {
            return;
        }
        Device robot = deviceRepository.findById(DemoDataService.ROBOT_ID).orElse(null);
        for (InspectionTask task : interrupted) {
            TaskStatus previous = task.getStatus();
            task.transitionTo(TaskStatus.WAITING_MANUAL, "平台重启后已同步记录，等待人工确认设备状态");
            taskRepository.save(task);
            eventRepository.save(new TaskEvent(task.getId(), previous, TaskStatus.WAITING_MANUAL,
                    "STARTUP_RECOVERY", "平台重启，任务转入等待人工处理"));
        }
        if (robot != null && robot.getActiveTaskId() != null) {
            robot.update(RobotState.SAFE_PARKED.name(), robot.isOnline(), robot.getBatteryPercent(),
                    robot.getPositionMeters(), robot.getActiveTaskId(), "平台已重启，等待人工确认设备状态");
            deviceRepository.save(robot);
        }
    }
}

