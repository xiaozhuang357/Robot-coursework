package com.lineguard.simulation;

import com.lineguard.domain.DomainTypes.TaskStatus;
import com.lineguard.service.MissionStateService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimulationEngineTest {

    /**
     * 推进失败且异常处置也失败时，调度记录必须被清理，
     * 否则该任务永远无法再次进入调度，只能重置演示环境。
     */
    @Test
    void reschedulesAfterFailureHandlingItselfFails() throws Exception {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.initialize();
        MissionStateService stateService = mock(MissionStateService.class);
        SimulationProperties properties = new SimulationProperties();
        properties.setEnabled(true);
        properties.setTickMillis(20);

        SimulationEngine engine = new SimulationEngine(scheduler, stateService, properties);
        UUID taskId = UUID.randomUUID();
        AtomicInteger advances = new AtomicInteger();

        when(stateService.advanceSimulation(taskId)).thenAnswer(invocation -> {
            if (advances.incrementAndGet() == 1) {
                throw new IllegalStateException("仿真推进异常");
            }
            return TaskStatus.SAFE_PARKED;
        });
        doThrow(new IllegalStateException("异常处置同样失败"))
                .when(stateService).handleSimulationFailure(any(), anyString());

        try {
            engine.startOrResume(taskId);
            Thread.sleep(200);
            engine.startOrResume(taskId);
            Thread.sleep(200);

            assertThat(advances.get())
                    .as("异常处置失败后仍必须能够重新调度该任务")
                    .isGreaterThanOrEqualTo(2);
        } finally {
            engine.stopAll();
            scheduler.shutdown();
        }
    }

    /** 暂停类状态应当结束调度，避免继续空转。 */
    @Test
    void stopsSchedulingWhenTheTaskPauses() throws Exception {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.initialize();
        MissionStateService stateService = mock(MissionStateService.class);
        SimulationProperties properties = new SimulationProperties();
        properties.setEnabled(true);
        properties.setTickMillis(20);

        SimulationEngine engine = new SimulationEngine(scheduler, stateService, properties);
        UUID taskId = UUID.randomUUID();
        AtomicInteger advances = new AtomicInteger();
        when(stateService.advanceSimulation(taskId)).thenAnswer(invocation -> {
            advances.incrementAndGet();
            return TaskStatus.AWAITING_REVIEW;
        });

        try {
            engine.startOrResume(taskId);
            Thread.sleep(200);
            assertThat(advances.get()).isEqualTo(1);
        } finally {
            engine.stopAll();
            scheduler.shutdown();
        }
    }
}
