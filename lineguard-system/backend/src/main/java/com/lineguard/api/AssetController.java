package com.lineguard.api;

import com.lineguard.api.ApiDtos.SegmentResponse;
import com.lineguard.service.MissionQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final MissionQueryService queryService;

    public AssetController(MissionQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/segments")
    public List<SegmentResponse> segments() {
        return queryService.segments();
    }
}

