package com.lineguard.realtime;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class RealtimeEventService {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

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
}

