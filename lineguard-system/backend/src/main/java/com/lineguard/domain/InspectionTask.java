package com.lineguard.domain;

import com.lineguard.domain.DomainTypes.TaskSource;
import com.lineguard.domain.DomainTypes.TaskStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inspection_tasks")
public class InspectionTask {

    @Id
    private UUID id;
    private String taskCode;
    @Enumerated(EnumType.STRING)
    private TaskSource source;
    private String inspectionPointId;
    private String inspectionItem;
    @Enumerated(EnumType.STRING)
    private TaskStatus status;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant startedAt;
    private Instant completedAt;
    private String statusMessage;
    @Version
    private long version;

    protected InspectionTask() {
    }

    public InspectionTask(UUID id, String taskCode, TaskSource source, String inspectionPointId,
                          String inspectionItem, Instant expiresAt) {
        this.id = id;
        this.taskCode = taskCode;
        this.source = source;
        this.inspectionPointId = inspectionPointId;
        this.inspectionItem = inspectionItem;
        this.status = TaskStatus.CREATED;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.statusMessage = "任务已创建，等待派发";
    }

    public void transitionTo(TaskStatus status, String message) {
        this.status = status;
        this.statusMessage = message;
        this.updatedAt = Instant.now();
        if (status == TaskStatus.DEPARTING && startedAt == null) {
            this.startedAt = this.updatedAt;
        }
        if (status == TaskStatus.COMPLETED || status == TaskStatus.ABORTED) {
            this.completedAt = this.updatedAt;
        }
    }

    public UUID getId() { return id; }
    public String getTaskCode() { return taskCode; }
    public TaskSource getSource() { return source; }
    public String getInspectionPointId() { return inspectionPointId; }
    public String getInspectionItem() { return inspectionItem; }
    public TaskStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getStatusMessage() { return statusMessage; }
}

