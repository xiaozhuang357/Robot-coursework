package com.lineguard.api;

import com.lineguard.api.ApiDtos.DeviceResponse;
import com.lineguard.service.MissionQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final MissionQueryService queryService;

    public DeviceController(MissionQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public List<DeviceResponse> devices() {
        return queryService.devices();
    }
}

