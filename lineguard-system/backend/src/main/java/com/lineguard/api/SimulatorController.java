package com.lineguard.api;

import com.lineguard.api.ApiDtos.FaultInjectionRequest;
import com.lineguard.service.MissionApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/simulator")
public class SimulatorController {

    private final MissionApplicationService applicationService;

    public SimulatorController(MissionApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/faults")
    public Map<String, Object> injectFault(@Valid @RequestBody FaultInjectionRequest request) {
        return Map.of("message", "仿真故障指令已处理", "status", applicationService.injectFault(request));
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() {
        applicationService.resetDemo();
    }
}

