package com.lineguard.simulation;

import com.lineguard.device.DeviceGateway;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SimulatedDeviceGateway implements DeviceGateway {

    private final SimulationEngine engine;

    public SimulatedDeviceGateway(SimulationEngine engine) {
        this.engine = engine;
    }

    @Override
    public void startOrResume(UUID taskId) {
        engine.startOrResume(taskId);
    }

    @Override
    public void stop(UUID taskId) {
        engine.stop(taskId);
    }

    @Override
    public void stopAll() {
        engine.stopAll();
    }
}

