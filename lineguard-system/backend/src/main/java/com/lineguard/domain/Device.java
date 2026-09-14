package com.lineguard.domain;

import com.lineguard.domain.DomainTypes.DeviceType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    private String id;
    private String name;
    @Enumerated(EnumType.STRING)
    private DeviceType type;
    private String state;
    private boolean online;
    private int batteryPercent;
    private double positionMeters;
    private UUID activeTaskId;
    private String statusMessage;
    private Instant updatedAt;
    @Version
    private long version;

    protected Device() {
    }

    public Device(String id, String name, DeviceType type, String state,
                  boolean online, int batteryPercent, double positionMeters, String statusMessage) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.state = state;
        this.online = online;
        this.batteryPercent = batteryPercent;
        this.positionMeters = positionMeters;
        this.statusMessage = statusMessage;
        this.updatedAt = Instant.now();
    }

    public void update(String state, boolean online, int batteryPercent,
                       double positionMeters, UUID activeTaskId, String statusMessage) {
        this.state = state;
        this.online = online;
        this.batteryPercent = Math.max(0, Math.min(100, batteryPercent));
        this.positionMeters = Math.max(0, positionMeters);
        this.activeTaskId = activeTaskId;
        this.statusMessage = statusMessage;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public DeviceType getType() { return type; }
    public String getState() { return state; }
    public boolean isOnline() { return online; }
    public int getBatteryPercent() { return batteryPercent; }
    public double getPositionMeters() { return positionMeters; }
    public UUID getActiveTaskId() { return activeTaskId; }
    public String getStatusMessage() { return statusMessage; }
    public Instant getUpdatedAt() { return updatedAt; }
}

