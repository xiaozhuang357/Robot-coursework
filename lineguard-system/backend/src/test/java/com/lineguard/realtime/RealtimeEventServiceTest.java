package com.lineguard.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeEventServiceTest {

    /**
     * 心跳的作用是保活并清掉已经离开的订阅。这里不手动调 sendHeartbeat：
     * 让调度器自己跑，才能同时证明"定时确实在跑"和"活着的订阅不会被误踢"。
     */
    @Test
    void heartbeatKeepsLiveSubscribersAndDropsTheOnesThatWentAway() throws Exception {
        RealtimeEventService service = new RealtimeEventService(30);
        try {
            service.subscribe();
            SseEmitter gone = service.subscribe();
            assertThat(service.subscribers()).isEqualTo(2);

            gone.complete();

            long deadline = System.currentTimeMillis() + 3000;
            while (service.subscribers() > 1 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }

            assertThat(service.subscribers())
                    .as("已经关闭的订阅应被心跳就地清理，不能攒在列表里")
                    .isEqualTo(1);

            // 再放过几轮心跳：活着的那一份必须一直留着，不能被逐轮误踢
            Thread.sleep(150);
            assertThat(service.subscribers()).as("活着的订阅不能被心跳误关").isEqualTo(1);
        } finally {
            service.stop();
        }
    }

    @Test
    void subscribersStartEmptyAndPublishingWithoutSubscribersIsSafe() {
        RealtimeEventService service = new RealtimeEventService(60_000);
        try {
            assertThat(service.subscribers()).isZero();
            service.publish("DEVICES_UPDATED", java.util.Map.of("robot", "x"));
            assertThat(service.subscribers()).isZero();
        } finally {
            service.stop();
        }
    }
}
