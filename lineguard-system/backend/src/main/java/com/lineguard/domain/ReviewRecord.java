package com.lineguard.domain;

import com.lineguard.domain.DomainTypes.ReviewConclusion;
import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_records", uniqueConstraints = @UniqueConstraint(columnNames = "task_id"))
public class ReviewRecord {

    @Id
    private UUID id;
    @Column(name = "task_id")
    private UUID taskId;
    @Enumerated(EnumType.STRING)
    private ReviewConclusion conclusion;
    private String remark;
    private String reviewer;
    private Instant reviewedAt;

    protected ReviewRecord() {
    }

    public ReviewRecord(UUID id, UUID taskId, ReviewConclusion conclusion, String remark, String reviewer) {
        this.id = id;
        this.taskId = taskId;
        this.conclusion = conclusion;
        this.remark = remark;
        this.reviewer = reviewer;
        this.reviewedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public ReviewConclusion getConclusion() { return conclusion; }
    public String getRemark() { return remark; }
    public String getReviewer() { return reviewer; }
    public Instant getReviewedAt() { return reviewedAt; }
}
