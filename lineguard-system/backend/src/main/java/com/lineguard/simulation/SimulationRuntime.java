package com.lineguard.simulation;

import com.lineguard.domain.DomainTypes.FaultType;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class SimulationRuntime {

    private final Map<UUID, EnumSet<FaultType>> faults = new HashMap<>();
    private final Map<UUID, Integer> dockingAttempts = new HashMap<>();

    public synchronized void setFault(UUID taskId, FaultType type, boolean enabled) {
        EnumSet<FaultType> taskFaults = faults.computeIfAbsent(taskId, ignored -> EnumSet.noneOf(FaultType.class));
        if (enabled) {
            taskFaults.add(type);
        } else {
            taskFaults.remove(type);
        }
    }

    public synchronized boolean hasFault(UUID taskId, FaultType type) {
        return faults.getOrDefault(taskId, EnumSet.noneOf(FaultType.class)).contains(type);
    }

    public synchronized boolean consumeFault(UUID taskId, FaultType type) {
        EnumSet<FaultType> taskFaults = faults.get(taskId);
        return taskFaults != null && taskFaults.remove(type);
    }

    public synchronized int nextDockingAttempt(UUID taskId) {
        int next = dockingAttempts.getOrDefault(taskId, 0) + 1;
        dockingAttempts.put(taskId, next);
        return next;
    }

    public synchronized void clearTask(UUID taskId) {
        faults.remove(taskId);
        dockingAttempts.remove(taskId);
    }

    public synchronized void clearAll() {
        faults.clear();
        dockingAttempts.clear();
    }
}

