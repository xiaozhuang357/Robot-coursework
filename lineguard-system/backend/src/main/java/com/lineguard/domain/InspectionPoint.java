package com.lineguard.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inspection_points")
public class InspectionPoint {

    @Id
    private String id;
    private String lineSegmentId;
    private String name;
    private double positionMeters;
    private String defaultInspectionItem;

    protected InspectionPoint() {
    }

    public InspectionPoint(String id, String lineSegmentId, String name,
                           double positionMeters, String defaultInspectionItem) {
        this.id = id;
        this.lineSegmentId = lineSegmentId;
        this.name = name;
        this.positionMeters = positionMeters;
        this.defaultInspectionItem = defaultInspectionItem;
    }

    public String getId() { return id; }
    public String getLineSegmentId() { return lineSegmentId; }
    public String getName() { return name; }
    public double getPositionMeters() { return positionMeters; }
    public String getDefaultInspectionItem() { return defaultInspectionItem; }
}

