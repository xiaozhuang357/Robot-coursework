package com.lineguard.simulation;

import com.lineguard.domain.DomainTypes.TaskStatus;
import com.lineguard.service.MissionStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
public class SimulationEngine {

    private static final Logger log = LoggerFactory.getLogger(SimulationEngine.class);

    private final ThreadPoolTaskScheduler scheduler;
    private final MissionStateService missionStateService;
    private final SimulationProperties properties;
    private final Map<UUID, ScheduledFuture<?>> jobs = new ConcurrentHashMap<>();

    public SimulationEngine(ThreadPoolTaskScheduler scheduler,
                            MissionStateService missionStateService,
                            SimulationProperties properties) {
        this.scheduler = scheduler;
        this.missionStateService = missionStateService;
        this.properties = properties;
    }

    public void startOrResume(UUID taskId) {
        if (!properties.isEnabled()) {
            return;
        }
        jobs.computeIfAbsent(taskId, id -> scheduler.scheduleWithFixedDelay(
                () -> tick(id), Duration.ofMillis(properties.getTickMillis())));
    }

    public void stop(UUID taskId) {
        ScheduledFuture<?> future = jobs.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }
    }

    public void stopAll() {
        jobs.keySet().forEach(this::stop);
    }

    private void tick(UUID taskId) {
        try {
            TaskStatus status = missionStateService.advanceSimulation(taskId);
            if (status.pausesSimulation()) {
                stop(taskId);
            }
        } catch (Exception exception) {
            log.error("任务 {} 的仿真推进失败", taskId, exception);
            try {
                missionStateService.handleSimulationFailure(taskId, exception.getMessage());
            } catch (Exception handlingFailure) {
                log.error("任务 {} 的异常处置同样失败，保留调度清理", taskId, handlingFailure);
            } finally {
                // 必须移除调度记录，否则该任务无法再通过 startOrResume 恢复推进。
                stop(taskId);
            }
        }
    }
}

