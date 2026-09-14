package com.lineguard.api;

import com.lineguard.realtime.RealtimeEventService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/events")
public class RealtimeController {

    private final RealtimeEventService realtime;

    public RealtimeController(RealtimeEventService realtime) {
        this.realtime = realtime;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return realtime.subscribe();
    }
}

