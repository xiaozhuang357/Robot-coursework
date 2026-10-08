package com.lineguard;

import com.lineguard.api.ApiDtos.CreateTaskRequest;
import com.lineguard.api.ApiDtos.FaultInjectionRequest;
import com.lineguard.api.ApiDtos.ReviewTaskRequest;
import com.lineguard.api.BusinessException;
import com.lineguard.domain.DomainTypes.DockState;
import com.lineguard.domain.DomainTypes.FaultType;
import com.lineguard.domain.DomainTypes.ReviewConclusion;
import com.lineguard.domain.DomainTypes.RobotState;
import com.lineguard.domain.DomainTypes.TaskSource;
import com.lineguard.domain.DomainTypes.TaskStatus;
import com.lineguard.domain.InspectionTask;
import com.lineguard.repository.DeviceRepository;
import com.lineguard.repository.TaskEventRepository;
import com.lineguard.service.DemoDataService;
import com.lineguard.service.MissionQueryService;
import com.lineguard.service.MissionStateService;
import com.lineguard.service.StartupRecoveryService;
import com.lineguard.simulation.SimulationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class MissionLifecycleIntegrationTest {

    @Autowired
    private DemoDataService demoDataService;
    @Autowired
    private MissionStateService missionStateService;
    @Autowired
    private MissionQueryService queryService;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private TaskEventRepository eventRepository;
    @Autowired
    private SimulationProperties properties;
    @Autowired
    private StartupRecoveryService startupRecoveryService;

    @BeforeEach
    void resetDemo() {
        demoDataService.reset();
    }

    @Test
    void completesTheCoreInspectionLoop() {
        InspectionTask task = createTask("LG-POINT-350");

        assertThat(missionStateService.dispatch(task.getId(), "dispatch-1"))
                .isEqualTo(TaskStatus.DEPARTING);
        advanceUntil(task.getId(), TaskStatus.AWAITING_REVIEW, 10);

        var beforeReview = queryService.task(task.getId());
        assertThat(beforeReview.latestDetection()).isNotNull();
        assertThat(beforeReview.latestDetection().inspectionPointId()).isEqualTo("LG-POINT-350");
        assertThat(beforeReview.latestDetection().positionMeters()).isEqualTo(350);

        missionStateService.reviewAndReturn(task.getId(), new ReviewTaskRequest(
                "review-1", ReviewConclusion.NO_ABNORMALITY, "附件外观正常", "测试运维员"));
        advanceUntil(task.getId(), TaskStatus.COMPLETED, 10);

        var completed = queryService.task(task.getId());
        assertThat(completed.task().status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(completed.review().conclusion()).isEqualTo(ReviewConclusion.NO_ABNORMALITY);
        assertThat(deviceRepository.findById(DemoDataService.ROBOT_ID).orElseThrow().getState())
                .isEqualTo("DOCKED");
        assertThat(deviceRepository.findById(DemoDataService.DOCK_ID).orElseThrow().getState())
                .isEqualTo("LOCKED_CHARGING");
        assertThat(completed.events()).extracting(event -> event.toStatus())
                .containsSubsequence(TaskStatus.CREATED, TaskStatus.PRECHECKING, TaskStatus.DEPARTING,
                        TaskStatus.MOVING, TaskStatus.INSPECTING, TaskStatus.UPLOADING,
                        TaskStatus.AWAITING_REVIEW, TaskStatus.RETURNING, TaskStatus.DOCKING,
                        TaskStatus.COMPLETED);
    }

    @Test
    void ignoresDuplicateDispatchCommand() {
        InspectionTask task = createTask("LG-POINT-350");
        missionStateService.dispatch(task.getId(), "same-command");
        missionStateService.dispatch(task.getId(), "same-command");

        long departureTransitions = eventRepository.findByTaskIdOrderByCreatedAtAsc(task.getId()).stream()
                .filter(event -> event.getFromStatus() == TaskStatus.PRECHECKING)
                .filter(event -> event.getToStatus() == TaskStatus.DEPARTING)
                .count();
        assertThat(departureTransitions).isEqualTo(1);
        assertThat(eventRepository.findByTaskIdOrderByCreatedAtAsc(task.getId()))
                .anyMatch(event -> event.getEventType().equals("DUPLICATE_COMMAND"));
    }

    @Test
    void lowBatteryTriggersReturnInsteadOfContinuingOutbound() {
        InspectionTask task = createTask("LG-POINT-720");
        missionStateService.dispatch(task.getId(), "dispatch-low");
        missionStateService.advanceSimulation(task.getId());
        missionStateService.advanceSimulation(task.getId());

        TaskStatus status = missionStateService.injectFault(new FaultInjectionRequest(
                task.getId(), FaultType.LOW_BATTERY, true));

        assertThat(status).isEqualTo(TaskStatus.RETURNING);
        assertThat(deviceRepository.findById(DemoDataService.ROBOT_ID).orElseThrow().getState())
                .isEqualTo("MOVING_RETURN");
        advanceUntil(task.getId(), TaskStatus.COMPLETED, 10);
    }

    @Test
    void communicationLossStopsTheRobotAndRequiresManualReturn() {
        InspectionTask task = createTask("LG-POINT-350");
        missionStateService.dispatch(task.getId(), "dispatch-comms");
        missionStateService.advanceSimulation(task.getId());
        missionStateService.advanceSimulation(task.getId());

        assertThat(missionStateService.injectFault(new FaultInjectionRequest(
                task.getId(), FaultType.COMMUNICATION_LOSS, true)))
                .isEqualTo(TaskStatus.WAITING_MANUAL);
        assertThat(deviceRepository.findById(DemoDataService.ROBOT_ID).orElseThrow().isOnline()).isFalse();

        missionStateService.injectFault(new FaultInjectionRequest(
                task.getId(), FaultType.COMMUNICATION_LOSS, false));
        assertThat(missionStateService.requestReturn(task.getId(), "manual-return"))
                .isEqualTo(TaskStatus.RETURNING);
    }

    @Test
    void dockingFailureRetriesTwiceThenSafeParks() {
        InspectionTask task = createTask("LG-POINT-350");
        missionStateService.dispatch(task.getId(), "dispatch-dock");
        advanceUntil(task.getId(), TaskStatus.AWAITING_REVIEW, 10);
        missionStateService.reviewAndReturn(task.getId(), new ReviewTaskRequest(
                "review-dock", ReviewConclusion.CONTINUE_OBSERVATION, "继续观察", "测试运维员"));
        missionStateService.injectFault(new FaultInjectionRequest(
                task.getId(), FaultType.DOCKING_FAILURE, true));
        advanceUntil(task.getId(), TaskStatus.DOCKING, 10);

        assertThat(missionStateService.advanceSimulation(task.getId())).isEqualTo(TaskStatus.DOCKING);
        assertThat(missionStateService.advanceSimulation(task.getId())).isEqualTo(TaskStatus.DOCKING);
        assertThat(missionStateService.advanceSimulation(task.getId())).isEqualTo(TaskStatus.SAFE_PARKED);
    }

    @Test
    void abortMidLineParksTheRobotAndRecallCompletesTheLoop() {
        InspectionTask task = createTask("LG-POINT-720");
        missionStateService.dispatch(task.getId(), "dispatch-abort");
        missionStateService.advanceSimulation(task.getId());
        missionStateService.advanceSimulation(task.getId());
        assertThat(deviceRepository.findById(DemoDataService.ROBOT_ID).orElseThrow().getPositionMeters())
                .isGreaterThan(0);

        assertThat(missionStateService.abort(task.getId(), "abort-1")).isEqualTo(TaskStatus.SAFE_PARKED);
        assertThat(deviceRepository.findById(DemoDataService.ROBOT_ID).orElseThrow().getState())
                .isEqualTo("SAFE_PARKED");

        // 终止后机器人仍在线路上，必须能够被召回并走完对接流程。
        assertThat(missionStateService.requestReturn(task.getId(), "recall-1"))
                .isEqualTo(TaskStatus.RETURNING);
        advanceUntil(task.getId(), TaskStatus.COMPLETED, 12);
        assertThat(deviceRepository.findById(DemoDataService.ROBOT_ID).orElseThrow().getState())
                .isEqualTo("DOCKED");
        assertThat(deviceRepository.findById(DemoDataService.DOCK_ID).orElseThrow().getState())
                .isEqualTo("LOCKED_CHARGING");
    }

    @Test
    void abortWhileDockedTerminatesTheTask() {
        InspectionTask task = createTask("LG-POINT-350");

        assertThat(missionStateService.abort(task.getId(), "abort-docked")).isEqualTo(TaskStatus.ABORTED);
        assertThat(deviceRepository.findById(DemoDataService.ROBOT_ID).orElseThrow().getState())
                .isEqualTo("DOCKED");
        assertThat(queryService.activeTask()).isEmpty();
    }

    @Test
    void lowerBatteryFaultKeepsEnoughChargeToReturn() {
        InspectionTask task = createTask("LG-POINT-720");
        missionStateService.dispatch(task.getId(), "dispatch-battery");
        missionStateService.advanceSimulation(task.getId());
        missionStateService.advanceSimulation(task.getId());

        missionStateService.injectFault(new FaultInjectionRequest(
                task.getId(), FaultType.LOW_BATTERY, true));

        // 注入电量由配置决定且必须高于临界值，否则返航会立刻退化为驻停，演示路径失效。
        assertThat(properties.getFaultBatteryPercent()).isGreaterThan(properties.getCriticalBattery());
        assertThat(deviceRepository.findById(DemoDataService.ROBOT_ID).orElseThrow().getBatteryPercent())
                .isEqualTo(properties.getFaultBatteryPercent());
        assertThat(missionStateService.advanceSimulation(task.getId()))
                .isIn(TaskStatus.RETURNING, TaskStatus.DOCKING);
    }

    @Test
    void obstacleStopsTheRobotAndBlocksReturnUntilCleared() {
        InspectionTask task = createTask("LG-POINT-350");
        missionStateService.dispatch(task.getId(), "dispatch-obstacle");
        missionStateService.advanceSimulation(task.getId());
        missionStateService.advanceSimulation(task.getId());

        assertThat(missionStateService.injectFault(new FaultInjectionRequest(
                task.getId(), FaultType.OBSTACLE, true)))
                .isEqualTo(TaskStatus.SAFE_PARKED);
        assertThat(robotState()).isEqualTo(RobotState.SAFE_PARKED.name());

        // 障碍未解除前不能移动：这是"不误执行"这条验收标准的实际落点
        assertThatThrownBy(() -> missionStateService.requestReturn(task.getId(), "blocked-return"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("障碍状态尚未解除");

        missionStateService.injectFault(new FaultInjectionRequest(
                task.getId(), FaultType.OBSTACLE, false));
        assertThat(missionStateService.requestReturn(task.getId(), "clear-return"))
                .isEqualTo(TaskStatus.RETURNING);
    }

    @Test
    void startupRecoveryReconcilesTheDockAsWellAsTheRobot() {
        InspectionTask task = createTask("LG-POINT-350");
        assertThat(missionStateService.dispatch(task.getId(), "dispatch-restart"))
                .isEqualTo(TaskStatus.DEPARTING);
        assertThat(dockState()).isEqualTo(DockState.RELEASING.name());

        startupRecoveryService.run(null);

        assertThat(queryService.task(task.getId()).task().status()).isEqualTo(TaskStatus.WAITING_MANUAL);
        assertThat(robotState()).isEqualTo(RobotState.SAFE_PARKED.name());
        // 重启前那次"正在释放"已经不再发生，坞必须回到等待返回：
        // 否则界面会一直显示释放中，而新任务的预检也永远等不到锁止充电
        assertThat(dockState()).isEqualTo(DockState.WAITING_RETURN.name());
    }

    @Test
    void startupRecoveryLeavesAHealthyStationAlone() {
        InspectionTask task = createTask("LG-POINT-350");
        missionStateService.dispatch(task.getId(), "dispatch-clean");
        advanceUntil(task.getId(), TaskStatus.AWAITING_REVIEW, 10);
        missionStateService.reviewAndReturn(task.getId(), new ReviewTaskRequest(
                "review-clean", ReviewConclusion.NO_ABNORMALITY, "附件外观正常", "测试运维员"));
        advanceUntil(task.getId(), TaskStatus.COMPLETED, 10);

        startupRecoveryService.run(null);

        // 已完成的任务不该被改写成等待人工，锁止充电中的坞也不该被降级成等待返回
        assertThat(queryService.task(task.getId()).task().status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(dockState()).isEqualTo(DockState.LOCKED_CHARGING.name());
        assertThat(robotState()).isEqualTo(RobotState.DOCKED.name());
    }

    @Test
    void taskListQueryCountDoesNotGrowWithTaskCount() {
        createTask("LG-POINT-350");
        createTask("LG-POINT-720");

        // 先校准这把尺子：同一份数据取两次，语句数必须翻倍。
        // Hibernate 自带的 prepareStatementCount 把预编译语句复用算成一条，过不了这一步；
        // 用它去断言 N+1 会假通过，所以这里挂的是逐条计数的 StatementInspector。
        int once = statementsForTaskList(1);
        assertThat(statementsForTaskList(2)).as("计数器必须逐条语句计数").isEqualTo(once * 2);

        // 关键性质：任务数翻倍后列表的语句数不变，说明检测点是一次批量取出来的。
        // 若改回逐个 findById，语句数会变成 1 + N，这个断言立刻红。
        createTask("LG-POINT-350");
        createTask("LG-POINT-720");
        assertThat(statementsForTaskList(1)).as("任务数翻倍后列表查询数不应增加").isEqualTo(once);
    }

    @Test
    void lowBatteryInjectionDoesNotBypassTheObstacleInterlock() {
        InspectionTask task = createTask("LG-POINT-350");
        missionStateService.dispatch(task.getId(), "dispatch-interlock");
        missionStateService.advanceSimulation(task.getId());
        missionStateService.advanceSimulation(task.getId());
        missionStateService.injectFault(new FaultInjectionRequest(task.getId(), FaultType.OBSTACLE, true));

        // 低电量自动返航必须和人工返航共用同一套联锁。否则"注入低电量"就是一条后门：
        // 机器人会带着未验证的障碍返航，最后 COMPLETED，而人工返航在同一状态下是被拒绝的。
        assertThat(missionStateService.injectFault(new FaultInjectionRequest(
                task.getId(), FaultType.LOW_BATTERY, true)))
                .isEqualTo(TaskStatus.SAFE_PARKED);
        assertThat(robotState()).isEqualTo(RobotState.SAFE_PARKED.name());
        assertThat(deviceRepository.findById(DemoDataService.ROBOT_ID).orElseThrow().getBatteryPercent())
                .as("电量仍然要如实注入")
                .isEqualTo(properties.getFaultBatteryPercent());
        assertThat(eventRepository.findByTaskIdOrderByCreatedAtAsc(task.getId()))
                .anyMatch(event -> event.getEventType().equals("FAULT_INJECTED")
                        && event.getMessage().contains("障碍状态尚未解除"));

        // 障碍解除后仍可人工召回并闭环
        missionStateService.injectFault(new FaultInjectionRequest(task.getId(), FaultType.OBSTACLE, false));
        assertThat(missionStateService.requestReturn(task.getId(), "recall-after-obstacle"))
                .isEqualTo(TaskStatus.RETURNING);
        advanceUntil(task.getId(), TaskStatus.COMPLETED, 12);
    }

    @Test
    void lowBatteryInjectionDoesNotResumeADisconnectedRobot() {
        InspectionTask task = createTask("LG-POINT-350");
        missionStateService.dispatch(task.getId(), "dispatch-offline");
        missionStateService.advanceSimulation(task.getId());
        missionStateService.advanceSimulation(task.getId());
        missionStateService.injectFault(new FaultInjectionRequest(task.getId(), FaultType.COMMUNICATION_LOSS, true));

        // 离线时注入低电量不能把机器人"唤醒"回返航，更不能顺带把离线状态抹掉：
        // 对接流程里 online 是被写死为 true 的，一旦放它走下去，通信中断会凭空消失。
        assertThat(missionStateService.injectFault(new FaultInjectionRequest(
                task.getId(), FaultType.LOW_BATTERY, true)))
                .isEqualTo(TaskStatus.WAITING_MANUAL);
        assertThat(deviceRepository.findById(DemoDataService.ROBOT_ID).orElseThrow().isOnline()).isFalse();

        // 恢复通信只改在线标志，去向由人工下达
        missionStateService.injectFault(new FaultInjectionRequest(task.getId(), FaultType.COMMUNICATION_LOSS, false));
        assertThat(deviceRepository.findById(DemoDataService.ROBOT_ID).orElseThrow().isOnline()).isTrue();
        assertThat(missionStateService.requestReturn(task.getId(), "manual-after-reconnect"))
                .isEqualTo(TaskStatus.RETURNING);
    }

    @Test
    void dockingRechargesTheRobotSoARepeatedMissionCanStillDispatch() {
        InspectionTask first = createTask("LG-POINT-350");
        missionStateService.dispatch(first.getId(), "dispatch-round-1");
        missionStateService.advanceSimulation(first.getId());
        missionStateService.advanceSimulation(first.getId());
        // 低电量返航会把电量压到注入值；对接后不回充的话，这个电量会原样带进下一轮
        missionStateService.injectFault(new FaultInjectionRequest(first.getId(), FaultType.LOW_BATTERY, true));
        advanceUntil(first.getId(), TaskStatus.COMPLETED, 12);

        // 坞的状态是 LOCKED_CHARGING，文案也承诺了"充电确认"，电量就必须真的回升，
        // 否则每轮净耗电，越过离站阈值后所有派发都预检失败，而唯一的出路是重置演示环境。
        assertThat(deviceRepository.findById(DemoDataService.ROBOT_ID).orElseThrow().getBatteryPercent())
                .isEqualTo(properties.getRechargedBatteryPercent());

        InspectionTask second = createTask("LG-POINT-720");
        assertThat(missionStateService.dispatch(second.getId(), "dispatch-round-2"))
                .isEqualTo(TaskStatus.DEPARTING);
    }

    @Test
    void rejectedFaultInjectionLeavesNoRuntimeFaultBehind() {
        InspectionTask task = createTask("LG-POINT-350");

        // CREATED 不允许迁移到 SAFE_PARKED，这条指令会被 409 拒绝。事务只回滚数据库，
        // 回滚不了 SimulationRuntime 里的标记，所以标记必须在迁移成功之后再写。
        assertThatThrownBy(() -> missionStateService.injectFault(new FaultInjectionRequest(
                task.getId(), FaultType.OBSTACLE, true)))
                .isInstanceOf(BusinessException.class);

        assertThat(missionStateService.dispatch(task.getId(), "dispatch-after-reject"))
                .isEqualTo(TaskStatus.DEPARTING);
        // 被拒绝的指令不能留下"影子障碍"，否则正常派发的任务会莫名停在原地
        assertThat(missionStateService.advanceSimulation(task.getId())).isEqualTo(TaskStatus.MOVING);
        assertThat(missionStateService.advanceSimulation(task.getId())).isEqualTo(TaskStatus.MOVING);
        assertThat(deviceRepository.findById(DemoDataService.ROBOT_ID).orElseThrow().getPositionMeters())
                .isGreaterThan(0);
    }

    @Test
    void faultInjectionIntoAFinishedTaskIsRejected() {
        InspectionTask task = createTask("LG-POINT-350");
        missionStateService.dispatch(task.getId(), "dispatch-finished");
        advanceUntil(task.getId(), TaskStatus.AWAITING_REVIEW, 10);
        missionStateService.reviewAndReturn(task.getId(), new ReviewTaskRequest(
                "review-finished", ReviewConclusion.NO_ABNORMALITY, "附件外观正常", "测试运维员"));
        advanceUntil(task.getId(), TaskStatus.COMPLETED, 10);

        // 对已结束的任务"恢复通信"曾经是唯一的越权入口：它会把 Robot 改成安全驻停，
        // 并把 activeTaskId 重新绑到这条已经结束的任务上，此后每次新建任务都预检失败。
        assertThatThrownBy(() -> missionStateService.injectFault(new FaultInjectionRequest(
                task.getId(), FaultType.COMMUNICATION_LOSS, false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务已结束");
        assertThat(robotState()).isEqualTo(RobotState.DOCKED.name());

        InspectionTask next = createTask("LG-POINT-720");
        assertThat(missionStateService.dispatch(next.getId(), "dispatch-next"))
                .isEqualTo(TaskStatus.DEPARTING);
    }

    private int statementsForTaskList(int calls) {
        SqlCounter.reset();
        for (int index = 0; index < calls; index++) {
            queryService.tasks();
        }
        return SqlCounter.count();
    }

    private String robotState() {
        return deviceRepository.findById(DemoDataService.ROBOT_ID).orElseThrow().getState();
    }

    private String dockState() {
        return deviceRepository.findById(DemoDataService.DOCK_ID).orElseThrow().getState();
    }

    private InspectionTask createTask(String pointId) {
        return missionStateService.createTask(new CreateTaskRequest(TaskSource.MANUAL, pointId, null, null));
    }

    private void advanceUntil(UUID taskId, TaskStatus expected, int maximumTicks) {
        for (int index = 0; index < maximumTicks; index++) {
            TaskStatus status = missionStateService.advanceSimulation(taskId);
            if (status == expected) return;
        }
        assertThat(queryService.task(taskId).task().status()).isEqualTo(expected);
    }
}

