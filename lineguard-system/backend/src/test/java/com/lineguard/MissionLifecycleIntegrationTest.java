package com.lineguard;

import com.lineguard.api.ApiDtos.CreateTaskRequest;
import com.lineguard.api.ApiDtos.FaultInjectionRequest;
import com.lineguard.api.ApiDtos.ReviewTaskRequest;
import com.lineguard.domain.DomainTypes.FaultType;
import com.lineguard.domain.DomainTypes.ReviewConclusion;
import com.lineguard.domain.DomainTypes.TaskSource;
import com.lineguard.domain.DomainTypes.TaskStatus;
import com.lineguard.domain.InspectionTask;
import com.lineguard.repository.DeviceRepository;
import com.lineguard.repository.TaskEventRepository;
import com.lineguard.service.DemoDataService;
import com.lineguard.service.MissionQueryService;
import com.lineguard.service.MissionStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

