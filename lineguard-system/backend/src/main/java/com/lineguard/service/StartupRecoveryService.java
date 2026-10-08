package com.lineguard.service;

import com.lineguard.domain.Device;
import com.lineguard.domain.DomainTypes.DockState;
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
import java.util.Set;

@Service
@Order(2)
public class StartupRecoveryService implements ApplicationRunner {

    /** 重启后必然已经失效的坞状态：它们描述的是"此刻正在动作"。 */
    private static final Set<String> TRANSIENT_DOCK_STATES = Set.of(
            DockState.RELEASING.name(), DockState.DOCKING.name(), DockState.FAULT.name());

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
        recoverDock();
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

    /** 重启后坞可能停在"正在释放 / 正在对接 / 故障"这类瞬时状态上。
     *
     * 这些状态断言的是"此刻正在发生某个动作"，而进程刚启动，那个动作必然已经不存在了；
     * 留在那里会让界面一直显示释放中或对接中，同时新任务的预检也永远等不到锁止充电。
     * 置为等待返回（站点空闲、等机器人回来），与 Robot 的等待人工确认口径一致；
     * 锁止与充电由下一次召回流程重新建立。空闲与已锁止的坞不动。
     */
    private void recoverDock() {
        Device dock = deviceRepository.findById(DemoDataService.DOCK_ID).orElse(null);
        if (dock == null || !TRANSIENT_DOCK_STATES.contains(dock.getState())) {
            return;
        }
        dock.update(DockState.WAITING_RETURN.name(), dock.isOnline(), dock.getBatteryPercent(), 0,
                dock.getActiveTaskId(), "平台已重启，站点回到等待返回状态");
        deviceRepository.save(dock);
    }
}

