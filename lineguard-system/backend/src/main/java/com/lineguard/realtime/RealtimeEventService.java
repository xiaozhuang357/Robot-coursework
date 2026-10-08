package com.lineguard.realtime;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

@Service
public class RealtimeEventService {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ThreadPoolTaskScheduler heartbeatScheduler;
    private final ScheduledFuture<?> heartbeat;

    public RealtimeEventService(
            @Value("${lineguard.realtime.heartbeat-millis:15000}") long heartbeatMillis) {
        this.heartbeatScheduler = new ThreadPoolTaskScheduler();
        this.heartbeatScheduler.setPoolSize(1);
        this.heartbeatScheduler.setThreadNamePrefix("lineguard-sse-");
        this.heartbeatScheduler.initialize();
        this.heartbeat = this.heartbeatScheduler.scheduleAtFixedRate(
                this::sendHeartbeat, Duration.ofMillis(heartbeatMillis));
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected")
                    .data(Map.of("type", "CONNECTED", "at", Instant.now().toString())));
        } catch (IOException exception) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void publish(String type, Object payload) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("lineguard-update")
                        .data(Map.of("type", type, "payload", payload, "at", Instant.now().toString())));
            } catch (IOException | IllegalStateException exception) {
                emitter.complete();
                emitters.remove(emitter);
            }
        }
    }

    /**
     * 保活：长时间没有事件时，中间的代理或 NAT 可能把连接悄悄掐掉，两端却都以为还活着——
     * 服务端攒着送不出去的连接，客户端也不知道自己该重连。定期发一行 SSE 注释把链路焐热；
     * 发送失败说明对端确实走了，就地清理。注释行不会触发前端的任何监听器，只作用于传输层。
     */
    void sendHeartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | IllegalStateException exception) {
                emitter.complete();
                emitters.remove(emitter);
            }
        }
    }

    /** 当前订阅者数量：供测试与运行观测。 */
    int subscribers() {
        return emitters.size();
    }

    @PreDestroy
    void stop() {
        heartbeat.cancel(false);
        heartbeatScheduler.shutdown();
    }
}
