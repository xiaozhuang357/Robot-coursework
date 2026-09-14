package com.lineguard.service;

import com.lineguard.api.ApiDtos.CreateTaskRequest;
import com.lineguard.api.ApiDtos.FaultInjectionRequest;
import com.lineguard.api.ApiDtos.ReviewTaskRequest;
import com.lineguard.api.BusinessException;
import com.lineguard.domain.CommandReceipt;
import com.lineguard.domain.DetectionRecord;
import com.lineguard.domain.Device;
import com.lineguard.domain.DomainTypes.CommandType;
import com.lineguard.domain.DomainTypes.DetectionQuality;
import com.lineguard.domain.DomainTypes.DockState;
import com.lineguard.domain.DomainTypes.FaultType;
import com.lineguard.domain.DomainTypes.RobotState;
import com.lineguard.domain.DomainTypes.TaskStatus;
import com.lineguard.domain.InspectionPoint;
import com.lineguard.domain.InspectionTask;
import com.lineguard.domain.ReviewRecord;
import com.lineguard.domain.TaskEvent;
import com.lineguard.realtime.RealtimeEventService;
import com.lineguard.repository.CommandReceiptRepository;
import com.lineguard.repository.DetectionRecordRepository;
import com.lineguard.repository.DeviceRepository;
import com.lineguard.repository.InspectionPointRepository;
import com.lineguard.repository.InspectionTaskRepository;
import com.lineguard.repository.ReviewRecordRepository;
import com.lineguard.repository.TaskEventRepository;
import com.lineguard.simulation.SimulationProperties;
import com.lineguard.simulation.SimulationRuntime;
import com.lineguard.storage.InspectionImageStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MissionStateService {

    private static final DateTimeFormatter CODE_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("Asia/Shanghai"));

    private final InspectionTaskRepository taskRepository;
    private final InspectionPointRepository pointRepository;
    private final DeviceRepository deviceRepository;
    private final TaskEventRepository eventRepository;
    private final DetectionRecordRepository detectionRepository;
    private final ReviewRecordRepository reviewRepository;
    private final CommandReceiptRepository commandRepository;
    private final TaskStateMachine stateMachine;
    private final SimulationProperties properties;
    private final SimulationRuntime runtime;
    private final InspectionImageStorage imageStorage;
    private final RealtimeEventService realtime;

    public MissionStateService(InspectionTaskRepository taskRepository,
                               InspectionPointRepository pointRepository,
                               DeviceRepository deviceRepository,
                               TaskEventRepository eventRepository,
                               DetectionRecordRepository detectionRepository,
                               ReviewRecordRepository reviewRepository,
                               CommandReceiptRepository commandRepository,
                               TaskStateMachine stateMachine,
                               SimulationProperties properties,
                               SimulationRuntime runtime,
                               InspectionImageStorage imageStorage,
                               RealtimeEventService realtime) {
        this.taskRepository = taskRepository;
        this.pointRepository = pointRepository;
        this.deviceRepository = deviceRepository;
        this.eventRepository = eventRepository;
        this.detectionRepository = detectionRepository;
        this.reviewRepository = reviewRepository;
        this.commandRepository = commandRepository;
        this.stateMachine = stateMachine;
        this.properties = properties;
        this.runtime = runtime;
        this.imageStorage = imageStorage;
        this.realtime = realtime;
    }

    @Transactional
    public synchronized InspectionTask createTask(CreateTaskRequest request) {
        InspectionPoint point = requirePoint(request.inspectionPointId());
        Instant expiresAt = request.expiresAt() == null ? Instant.now().plusSeconds(3600) : request.expiresAt();
        String item = request.inspectionItem() == null || request.inspectionItem().isBlank()
                ? point.getDefaultInspectionItem() : request.inspectionItem().trim();
        UUID id = UUID.randomUUID();
        String code = "LG-" + CODE_TIME.format(Instant.now()) + "-" + id.toString().substring(0, 4).toUpperCase();
        InspectionTask task = taskRepository.save(new InspectionTask(id, code, request.source(),
                point.getId(), item, expiresAt));
        eventRepository.save(new TaskEvent(id, null, TaskStatus.CREATED, "TASK_CREATED", "任务已创建"));
        publishTask(task);
        return task;
    }

    @Transactional
    public synchronized TaskStatus dispatch(UUID taskId, String commandId) {
        InspectionTask task = requireTask(taskId);
        if (!acceptCommand(task, commandId, CommandType.DISPATCH)) {
            return task.getStatus();
        }
        if (task.getStatus() != TaskStatus.CREATED && task.getStatus() != TaskStatus.PRECHECK_FAILED) {
            throw conflict("当前任务状态不允许派发");
        }
        taskRepository.findFirstByStatusInOrderByCreatedAtDesc(TaskStatus.activeStatuses())
                .filter(active -> !active.getId().equals(taskId))
                .ifPresent(active -> {
                    throw conflict("Robot 正在执行任务 " + active.getTaskCode());
                });

        transition(task, TaskStatus.PRECHECKING, "正在检查 Robot、Dock、通信、电量和任务有效期");
        Device robot = requireRobot();
        Device dock = requireDock();
        List<String> failures = new ArrayList<>();
        if (!robot.isOnline()) failures.add("Robot 离线");
        if (!dock.isOnline()) failures.add("Dock 离线");
        if (!RobotState.DOCKED.name().equals(robot.getState())) failures.add("Robot 未处于驻留状态");
        if (!DockState.LOCKED_CHARGING.name().equals(dock.getState())) failures.add("Dock 未完成锁止和充电确认");
        if (robot.getBatteryPercent() < properties.getMinimumDepartureBattery()) failures.add("Robot 电量不足");
        if (task.getExpiresAt().isBefore(Instant.now())) failures.add("任务已过有效期");
        if (!failures.isEmpty()) {
            transition(task, TaskStatus.PRECHECK_FAILED, String.join("；", failures));
            return task.getStatus();
        }

        robot.update(RobotState.UNDOCKING.name(), true, robot.getBatteryPercent(), 0,
                taskId, "解除驻留锁止，准备离站");
        dock.update(DockState.RELEASING.name(), true, dock.getBatteryPercent(), 0,
                taskId, "Dock 正在释放 Robot");
        deviceRepository.saveAll(List.of(robot, dock));
        transition(task, TaskStatus.DEPARTING, "预检通过，Robot 开始离站");
        publishDevices(robot, dock);
        return task.getStatus();
    }

    @Transactional
    public synchronized TaskStatus advanceSimulation(UUID taskId) {
        InspectionTask task = requireTask(taskId);
        Device robot = requireRobot();
        Device dock = requireDock();
        InspectionPoint point = requirePoint(task.getInspectionPointId());

        return switch (task.getStatus()) {
            case DEPARTING -> {
                robot.update(RobotState.MOVING_OUTBOUND.name(), robot.isOnline(),
                        robot.getBatteryPercent() - 1, robot.getPositionMeters(), taskId, "沿标定线路前往检测点");
                dock.update(DockState.WAITING_RETURN.name(), dock.isOnline(), dock.getBatteryPercent(),
                        0, taskId, "Robot 已离站，等待返回");
                deviceRepository.saveAll(List.of(robot, dock));
                transition(task, TaskStatus.MOVING, "Robot 已离站，开始沿线移动");
                publishDevices(robot, dock);
                yield task.getStatus();
            }
            case MOVING -> moveOutbound(task, point, robot, dock);
            case INSPECTING -> {
                transition(task, TaskStatus.UPLOADING, "检测图像已采集，正在上传");
                yield task.getStatus();
            }
            case UPLOADING -> uploadDetection(task, point, robot, dock);
            case RETURNING -> moveReturn(task, robot, dock);
            case DOCKING -> performDocking(task, robot, dock);
            default -> task.getStatus();
        };
    }

    private TaskStatus moveOutbound(InspectionTask task, InspectionPoint point, Device robot, Device dock) {
        if (robot.getBatteryPercent() <= properties.getCriticalBattery()) {
            return safePark(task, robot, dock, "电量低于安全返程阈值，Robot 原地驻停");
        }
        if (runtime.hasFault(task.getId(), FaultType.OBSTACLE)) {
            return safePark(task, robot, dock, "前方发现未验证障碍，Robot 停止移动");
        }
        double nextPosition = Math.min(point.getPositionMeters(),
                robot.getPositionMeters() + properties.getStepMeters());
        robot.update(RobotState.MOVING_OUTBOUND.name(), robot.isOnline(),
                robot.getBatteryPercent() - 1, nextPosition, task.getId(),
                "距 Dock " + Math.round(nextPosition) + " m");
        deviceRepository.save(robot);
        recordEvent(task, "TELEMETRY", "Robot 前进至 " + Math.round(nextPosition) + " m");
        publishDevices(robot, dock);
        if (nextPosition >= point.getPositionMeters()) {
            robot.update(RobotState.INSPECTING.name(), robot.isOnline(), robot.getBatteryPercent(),
                    nextPosition, task.getId(), "已到达检测点，执行定点拍摄");
            deviceRepository.save(robot);
            transition(task, TaskStatus.INSPECTING, "Robot 已到达 " + point.getName());
        }
        return task.getStatus();
    }

    private TaskStatus uploadDetection(InspectionTask task, InspectionPoint point, Device robot, Device dock) {
        int captureIndex = Math.toIntExact(detectionRepository.countByTaskId(task.getId()) + 1);
        DetectionQuality quality = runtime.consumeFault(task.getId(), FaultType.BLURRED_IMAGE)
                ? DetectionQuality.UNREADABLE : DetectionQuality.READABLE;
        String fileName = imageStorage.create(task.getId(), task.getTaskCode(), point.getName(),
                robot.getPositionMeters(), quality, captureIndex);
        detectionRepository.save(new DetectionRecord(UUID.randomUUID(), task.getId(), point.getId(),
                robot.getPositionMeters(), fileName, quality, captureIndex));
        robot.update(RobotState.WAITING_REVIEW.name(), robot.isOnline(), robot.getBatteryPercent(),
                robot.getPositionMeters(), task.getId(), quality == DetectionQuality.READABLE
                        ? "图像已上传，等待人工复核" : "图像不可判读，等待重新采集");
        deviceRepository.save(robot);
        transition(task, TaskStatus.AWAITING_REVIEW, quality == DetectionQuality.READABLE
                ? "检测数据已上传，等待人工复核" : "图像质量不足，可执行一次重新采集");
        publishDevices(robot, dock);
        return task.getStatus();
    }

    private TaskStatus moveReturn(InspectionTask task, Device robot, Device dock) {
        if (robot.getBatteryPercent() <= properties.getCriticalBattery()) {
            return safePark(task, robot, dock, "返程电量低于安全阈值，Robot 原地驻停");
        }
        double nextPosition = Math.max(0, robot.getPositionMeters() - properties.getStepMeters());
        robot.update(RobotState.MOVING_RETURN.name(), robot.isOnline(), robot.getBatteryPercent() - 1,
                nextPosition, task.getId(), "返程中，距 Dock " + Math.round(nextPosition) + " m");
        deviceRepository.save(robot);
        recordEvent(task, "TELEMETRY", "Robot 返程至 " + Math.round(nextPosition) + " m");
        publishDevices(robot, dock);
        if (nextPosition <= 0) {
            robot.update(RobotState.DOCKING.name(), robot.isOnline(), robot.getBatteryPercent(),
                    0, task.getId(), "进入低速对接区");
            dock.update(DockState.DOCKING.name(), dock.isOnline(), dock.getBatteryPercent(),
                    0, task.getId(), "正在确认位置、锁止与充电状态");
            deviceRepository.saveAll(List.of(robot, dock));
            transition(task, TaskStatus.DOCKING, "Robot 已进入 Dock 对接区");
        }
        return task.getStatus();
    }

    private TaskStatus performDocking(InspectionTask task, Device robot, Device dock) {
        if (runtime.hasFault(task.getId(), FaultType.DOCKING_FAILURE)) {
            int attempt = runtime.nextDockingAttempt(task.getId());
            if (attempt <= properties.getDockingMaxRetries()) {
                recordEvent(task, "DOCK_RETRY", "Dock 对接失败，执行第 " + attempt + " 次重试");
                return task.getStatus();
            }
            dock.update(DockState.FAULT.name(), dock.isOnline(), dock.getBatteryPercent(), 0,
                    task.getId(), "对接重试次数已用尽");
            return safePark(task, robot, dock, "Dock 对接失败，Robot 保持挂线驻停");
        }

        robot.update(RobotState.DOCKED.name(), true, robot.getBatteryPercent(), 0,
                null, "机械锁止、充电确认和数据同步完成");
        dock.update(DockState.LOCKED_CHARGING.name(), true, dock.getBatteryPercent(), 0,
                null, "Robot 已安全驻留并开始补能");
        deviceRepository.saveAll(List.of(robot, dock));
        transition(task, TaskStatus.COMPLETED, "任务闭环完成，Robot 已返回 Dock");
        runtime.clearTask(task.getId());
        publishDevices(robot, dock);
        return task.getStatus();
    }

    @Transactional
    public synchronized TaskStatus reviewAndReturn(UUID taskId, ReviewTaskRequest request) {
        InspectionTask task = requireTask(taskId);
        if (!acceptCommand(task, request.commandId(), CommandType.REVIEW_AND_RETURN)) {
            return task.getStatus();
        }
        if (task.getStatus() != TaskStatus.AWAITING_REVIEW) {
            throw conflict("当前任务不处于待复核状态");
        }
        DetectionRecord detection = detectionRepository.findFirstByTaskIdOrderByCapturedAtDesc(taskId)
                .orElseThrow(() -> conflict("任务没有可复核的检测记录"));
        if (detection.getQuality() != DetectionQuality.READABLE) {
            throw conflict("当前图像不可判读，请先重新采集");
        }
        if (reviewRepository.findByTaskId(taskId).isEmpty()) {
            String reviewer = request.reviewer() == null || request.reviewer().isBlank()
                    ? "演示运维员" : request.reviewer().trim();
            reviewRepository.save(new ReviewRecord(UUID.randomUUID(), taskId, request.conclusion(),
                    request.remark() == null ? "" : request.remark().trim(), reviewer));
        }
        Device robot = requireRobot();
        robot.update(RobotState.MOVING_RETURN.name(), robot.isOnline(), robot.getBatteryPercent(),
                robot.getPositionMeters(), taskId, "人工复核完成，开始返航");
        deviceRepository.save(robot);
        transition(task, TaskStatus.RETURNING, "人工复核完成，Robot 开始返回 Dock");
        return task.getStatus();
    }

    @Transactional
    public synchronized TaskStatus recapture(UUID taskId, String commandId) {
        InspectionTask task = requireTask(taskId);
        if (!acceptCommand(task, commandId, CommandType.RECAPTURE)) {
            return task.getStatus();
        }
        if (task.getStatus() != TaskStatus.AWAITING_REVIEW) {
            throw conflict("当前任务不能重新采集");
        }
        DetectionRecord detection = detectionRepository.findFirstByTaskIdOrderByCapturedAtDesc(taskId)
                .orElseThrow(() -> conflict("任务没有检测记录"));
        if (detection.getQuality() == DetectionQuality.READABLE) {
            throw conflict("当前图像已经可判读，无需重新采集");
        }
        if (detectionRepository.countByTaskId(taskId) >= 2) {
            throw conflict("重新采集次数已用尽");
        }
        Device robot = requireRobot();
        robot.update(RobotState.INSPECTING.name(), robot.isOnline(), robot.getBatteryPercent(),
                robot.getPositionMeters(), taskId, "执行一次重新采集");
        deviceRepository.save(robot);
        transition(task, TaskStatus.INSPECTING, "运维人员要求重新采集图像");
        return task.getStatus();
    }

    @Transactional
    public synchronized TaskStatus requestReturn(UUID taskId, String commandId) {
        InspectionTask task = requireTask(taskId);
        if (!acceptCommand(task, commandId, CommandType.RETURN)) {
            return task.getStatus();
        }
        if (!(task.getStatus() == TaskStatus.AWAITING_REVIEW || task.getStatus() == TaskStatus.WAITING_MANUAL
                || task.getStatus() == TaskStatus.SAFE_PARKED || task.getStatus() == TaskStatus.MOVING
                || task.getStatus() == TaskStatus.INSPECTING || task.getStatus() == TaskStatus.UPLOADING
                || task.getStatus() == TaskStatus.DEPARTING)) {
            throw conflict("当前任务不能执行人工返航");
        }
        Device robot = requireRobot();
        if (!robot.isOnline()) {
            throw conflict("Robot 当前离线，不能下达返航指令");
        }
        if (runtime.hasFault(taskId, FaultType.OBSTACLE)) {
            throw conflict("障碍状态尚未解除，不能继续移动");
        }
        robot.update(RobotState.MOVING_RETURN.name(), true, robot.getBatteryPercent(),
                robot.getPositionMeters(), taskId, "收到人工返航指令");
        deviceRepository.save(robot);
        transition(task, TaskStatus.RETURNING, "运维人员下达返航指令");
        return task.getStatus();
    }

    @Transactional
    public synchronized TaskStatus abort(UUID taskId, String commandId) {
        InspectionTask task = requireTask(taskId);
        if (!acceptCommand(task, commandId, CommandType.ABORT)) {
            return task.getStatus();
        }
        if (task.getStatus().isTerminal()) {
            return task.getStatus();
        }
        Device robot = requireRobot();
        Device dock = requireDock();
        boolean atDock = robot.getPositionMeters() <= 0;
        robot.update(atDock ? RobotState.DOCKED.name() : RobotState.SAFE_PARKED.name(),
                robot.isOnline(), robot.getBatteryPercent(), robot.getPositionMeters(),
                atDock ? null : taskId, atDock ? "任务终止，Robot 保持驻留" : "任务终止，Robot 保持挂线驻停");
        if (atDock) {
            dock.update(DockState.LOCKED_CHARGING.name(), dock.isOnline(), dock.getBatteryPercent(),
                    0, null, "Robot 已驻留");
        }
        deviceRepository.saveAll(List.of(robot, dock));
        transition(task, TaskStatus.ABORTED, "任务已由运维人员终止");
        publishDevices(robot, dock);
        return task.getStatus();
    }

    @Transactional
    public synchronized TaskStatus injectFault(FaultInjectionRequest request) {
        InspectionTask task = request.taskId() == null
                ? taskRepository.findFirstByStatusInOrderByCreatedAtDesc(TaskStatus.activeStatuses())
                    .orElseThrow(() -> conflict("当前没有可注入故障的运行任务"))
                : requireTask(request.taskId());
        boolean enabled = request.enabled() == null || request.enabled();
        runtime.setFault(task.getId(), request.type(), enabled);
        Device robot = requireRobot();
        Device dock = requireDock();

        switch (request.type()) {
            case LOW_BATTERY -> {
                robot.update(robot.getState(), robot.isOnline(), 18, robot.getPositionMeters(),
                        task.getId(), "仿真注入：返程储备电量偏低");
                deviceRepository.save(robot);
                if (!task.getStatus().isTerminal() && robot.getPositionMeters() > 0
                        && task.getStatus() != TaskStatus.RETURNING && task.getStatus() != TaskStatus.DOCKING) {
                    robot.update(RobotState.MOVING_RETURN.name(), robot.isOnline(), 18,
                            robot.getPositionMeters(), task.getId(), "低电量策略触发自动返航");
                    transition(task, TaskStatus.RETURNING, "检测到低电量，任务转入安全返航");
                } else {
                    recordEvent(task, "FAULT_INJECTED", "已注入低电量状态");
                }
            }
            case COMMUNICATION_LOSS -> {
                robot.update(enabled ? RobotState.SAFE_PARKED.name() : RobotState.SAFE_PARKED.name(),
                        !enabled, robot.getBatteryPercent(), robot.getPositionMeters(), task.getId(),
                        enabled ? "通信中断，本地安全停车" : "通信已恢复，等待状态确认");
                deviceRepository.save(robot);
                if (enabled && task.getStatus() != TaskStatus.WAITING_MANUAL) {
                    transition(task, TaskStatus.WAITING_MANUAL, "通信中断，Robot 本地停车并等待人工处理");
                } else {
                    recordEvent(task, enabled ? "FAULT_INJECTED" : "FAULT_CLEARED",
                            enabled ? "已注入通信中断" : "通信恢复，设备状态已重新同步");
                }
            }
            case OBSTACLE -> {
                if (enabled) {
                    robot.update(RobotState.SAFE_PARKED.name(), robot.isOnline(), robot.getBatteryPercent(),
                            robot.getPositionMeters(), task.getId(), "前方存在未验证障碍");
                    deviceRepository.save(robot);
                    if (task.getStatus() != TaskStatus.SAFE_PARKED) {
                        transition(task, TaskStatus.SAFE_PARKED, "检测到障碍，Robot 停止移动");
                    }
                } else {
                    recordEvent(task, "FAULT_CLEARED", "障碍状态已由演示控制台解除");
                }
            }
            case DOCKING_FAILURE -> recordEvent(task, enabled ? "FAULT_ARMED" : "FAULT_CLEARED",
                    enabled ? "已设置 Dock 对接失败，将在返航时触发" : "Dock 对接故障已解除");
            case BLURRED_IMAGE -> recordEvent(task, enabled ? "FAULT_ARMED" : "FAULT_CLEARED",
                    enabled ? "已设置下一张图像为不可判读" : "图像模糊故障已解除");
        }
        publishDevices(robot, dock);
        return task.getStatus();
    }

    @Transactional
    public synchronized void handleSimulationFailure(UUID taskId, String reason) {
        InspectionTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null || task.getStatus().isTerminal() || task.getStatus() == TaskStatus.WAITING_MANUAL) {
            return;
        }
        Device robot = requireRobot();
        Device dock = requireDock();
        robot.update(RobotState.SAFE_PARKED.name(), robot.isOnline(), robot.getBatteryPercent(),
                robot.getPositionMeters(), taskId, "仿真程序异常，设备已安全驻停");
        deviceRepository.save(robot);
        transition(task, TaskStatus.WAITING_MANUAL,
                "仿真推进异常，等待人工处理：" + (reason == null ? "未知原因" : reason));
        publishDevices(robot, dock);
    }

    private TaskStatus safePark(InspectionTask task, Device robot, Device dock, String message) {
        robot.update(RobotState.SAFE_PARKED.name(), robot.isOnline(), robot.getBatteryPercent(),
                robot.getPositionMeters(), task.getId(), message);
        deviceRepository.save(robot);
        if (task.getStatus() == TaskStatus.SAFE_PARKED) {
            recordEvent(task, "SAFE_PARK", message);
        } else {
            transition(task, TaskStatus.SAFE_PARKED, message);
        }
        publishDevices(robot, dock);
        return task.getStatus();
    }

    private boolean acceptCommand(InspectionTask task, String commandId, CommandType type) {
        if (commandId == null || commandId.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "commandId 不能为空");
        }
        if (commandRepository.existsByTaskIdAndCommandId(task.getId(), commandId)) {
            recordEvent(task, "DUPLICATE_COMMAND", "重复指令已忽略：" + commandId);
            return false;
        }
        commandRepository.save(new CommandReceipt(task.getId(), commandId, type));
        return true;
    }

    private void transition(InspectionTask task, TaskStatus to, String message) {
        TaskStatus from = task.getStatus();
        stateMachine.check(from, to);
        task.transitionTo(to, message);
        taskRepository.save(task);
        eventRepository.save(new TaskEvent(task.getId(), from, to, "STATUS_CHANGED", message));
        publishTask(task);
    }

    private void recordEvent(InspectionTask task, String type, String message) {
        eventRepository.save(new TaskEvent(task.getId(), task.getStatus(), task.getStatus(), type, message));
        realtime.publish("TASK_EVENT", Map.of("taskId", task.getId(), "status", task.getStatus(), "message", message));
    }

    private void publishTask(InspectionTask task) {
        realtime.publish("TASK_UPDATED", Map.of("taskId", task.getId(), "status", task.getStatus()));
    }

    private void publishDevices(Device robot, Device dock) {
        realtime.publish("DEVICES_UPDATED", Map.of("robotState", robot.getState(), "dockState", dock.getState(),
                "positionMeters", robot.getPositionMeters(), "batteryPercent", robot.getBatteryPercent()));
    }

    private InspectionTask requireTask(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    private InspectionPoint requirePoint(String pointId) {
        return pointRepository.findById(pointId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "检测点不存在"));
    }

    private Device requireRobot() {
        return deviceRepository.findById(DemoDataService.ROBOT_ID)
                .orElseThrow(() -> new IllegalStateException("Robot 演示设备不存在"));
    }

    private Device requireDock() {
        return deviceRepository.findById(DemoDataService.DOCK_ID)
                .orElseThrow(() -> new IllegalStateException("Dock 演示设备不存在"));
    }

    private BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }
}
