package com.lineguard.api;

import com.lineguard.domain.DetectionRecord;
import com.lineguard.service.MissionQueryService;
import com.lineguard.storage.InspectionImageStorage;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/detections")
public class DetectionController {

    private final MissionQueryService queryService;
    private final InspectionImageStorage imageStorage;

    public DetectionController(MissionQueryService queryService, InspectionImageStorage imageStorage) {
        this.queryService = queryService;
        this.imageStorage = imageStorage;
    }

    @GetMapping("/{detectionId}/image")
    public ResponseEntity<Resource> image(@PathVariable UUID detectionId) {
        DetectionRecord record = queryService.detection(detectionId);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/svg+xml"))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)))
                .body(imageStorage.load(record.getImageFileName()));
    }
}
