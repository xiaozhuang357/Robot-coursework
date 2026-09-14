package com.lineguard.domain;

import com.lineguard.domain.DomainTypes.DetectionQuality;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "detection_records")
public class DetectionRecord {

    @Id
    private UUID id;
    private UUID taskId;
    private String inspectionPointId;
    private double positionMeters;
    private String imageFileName;
    @Enumerated(EnumType.STRING)
    private DetectionQuality quality;
    private int captureIndex;
    private Instant capturedAt;

    protected DetectionRecord() {
    }

    public DetectionRecord(UUID id, UUID taskId, String inspectionPointId, double positionMeters,
                           String imageFileName, DetectionQuality quality, int captureIndex) {
        this.id = id;
        this.taskId = taskId;
        this.inspectionPointId = inspectionPointId;
        this.positionMeters = positionMeters;
        this.imageFileName = imageFileName;
        this.quality = quality;
        this.captureIndex = captureIndex;
        this.capturedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public String getInspectionPointId() { return inspectionPointId; }
    public double getPositionMeters() { return positionMeters; }
    public String getImageFileName() { return imageFileName; }
    public DetectionQuality getQuality() { return quality; }
    public int getCaptureIndex() { return captureIndex; }
    public Instant getCapturedAt() { return capturedAt; }
}

