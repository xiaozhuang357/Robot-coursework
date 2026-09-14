package com.lineguard.service;

import com.lineguard.api.ApiDtos.CommandRequest;
import com.lineguard.api.ApiDtos.CreateTaskRequest;
import com.lineguard.api.ApiDtos.FaultInjectionRequest;
import com.lineguard.api.ApiDtos.ReviewTaskRequest;
import com.lineguard.device.DeviceGateway;
import com.lineguard.domain.DomainTypes.TaskStatus;
import com.lineguard.domain.InspectionTask;
import com.lineguard.simulation.SimulationRuntime;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MissionApplicationService {

    private final MissionStateService stateService;
    private final DeviceGateway deviceGateway;
    private final DemoDataService demoDataService;
    private final SimulationRuntime simulationRuntime;

    public MissionApplicationService(MissionStateService stateService,
                                     DeviceGateway deviceGateway,
                                     DemoDataService demoDataService,
                                     SimulationRuntime simulationRuntime) {
        this.stateService = stateService;
        this.deviceGateway = deviceGateway;
        this.demoDataService = demoDataService;
        this.simulationRuntime = simulationRuntime;
    }

    public InspectionTask create(CreateTaskRequest request) {
        return stateService.createTask(request);
    }

    public TaskStatus dispatch(UUID taskId, CommandRequest request) {
        TaskStatus status = stateService.dispatch(taskId, request.commandId());
        if (status == TaskStatus.DEPARTING || status == TaskStatus.MOVING) {
            deviceGateway.startOrResume(taskId);
        }
        return status;
    }

    public TaskStatus reviewAndReturn(UUID taskId, ReviewTaskRequest request) {
        TaskStatus status = stateService.reviewAndReturn(taskId, request);
        if (status == TaskStatus.RETURNING) {
            deviceGateway.startOrResume(taskId);
        }
        return status;
    }

    public TaskStatus recapture(UUID taskId, CommandRequest request) {
        TaskStatus status = stateService.recapture(taskId, request.commandId());
        if (status == TaskStatus.INSPECTING) {
            deviceGateway.startOrResume(taskId);
        }
        return status;
    }

    public TaskStatus requestReturn(UUID taskId, CommandRequest request) {
        TaskStatus status = stateService.requestReturn(taskId, request.commandId());
        if (status == TaskStatus.RETURNING) {
            deviceGateway.startOrResume(taskId);
        }
        return status;
    }

    public TaskStatus abort(UUID taskId, CommandRequest request) {
        TaskStatus status = stateService.abort(taskId, request.commandId());
        deviceGateway.stop(taskId);
        return status;
    }

    public TaskStatus injectFault(FaultInjectionRequest request) {
        TaskStatus status = stateService.injectFault(request);
        UUID taskId = request.taskId();
        if (taskId != null) {
            if (status.pausesSimulation()) {
                deviceGateway.stop(taskId);
            } else if (status == TaskStatus.RETURNING) {
                deviceGateway.startOrResume(taskId);
            }
        }
        return status;
    }

    public void resetDemo() {
        deviceGateway.stopAll();
        simulationRuntime.clearAll();
        demoDataService.reset();
    }
}

