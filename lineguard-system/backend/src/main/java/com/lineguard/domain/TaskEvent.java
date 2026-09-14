package com.lineguard.domain;

import com.lineguard.domain.DomainTypes.TaskStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_events")
public class TaskEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID taskId;
    @Enumerated(EnumType.STRING)
    private TaskStatus fromStatus;
    @Enumerated(EnumType.STRING)
    private TaskStatus toStatus;
    private String eventType;
    private String message;
    private Instant createdAt;

    protected TaskEvent() {
    }

    public TaskEvent(UUID taskId, TaskStatus fromStatus, TaskStatus toStatus,
                     String eventType, String message) {
        this.taskId = taskId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.eventType = eventType;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public TaskStatus getFromStatus() { return fromStatus; }
    public TaskStatus getToStatus() { return toStatus; }
    public String getEventType() { return eventType; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}

