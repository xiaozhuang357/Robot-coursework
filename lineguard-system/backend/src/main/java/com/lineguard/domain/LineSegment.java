package com.lineguard.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "line_segments")
public class LineSegment {

    @Id
    private String id;
    private String name;
    private double lengthMeters;
    private String description;

    protected LineSegment() {
    }

    public LineSegment(String id, String name, double lengthMeters, String description) {
        this.id = id;
        this.name = name;
        this.lengthMeters = lengthMeters;
        this.description = description;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getLengthMeters() { return lengthMeters; }
    public String getDescription() { return description; }
}

