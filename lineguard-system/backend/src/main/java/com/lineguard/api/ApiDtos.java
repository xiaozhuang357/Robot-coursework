package com.lineguard.api;

import com.lineguard.domain.DomainTypes.DetectionQuality;
import com.lineguard.domain.DomainTypes.DeviceType;
import com.lineguard.domain.DomainTypes.FaultType;
import com.lineguard.domain.DomainTypes.ReviewConclusion;
import com.lineguard.domain.DomainTypes.TaskSource;
import com.lineguard.domain.DomainTypes.TaskStatus;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApiDtos {

    private ApiDtos() {
    }

    public record CreateTaskRequest(
            @NotNull TaskSource source,
            @NotBlank String inspectionPointId,
            @Size(max = 120) String inspectionItem,
            @Future Instant expiresAt) {
    }

    public record CommandRequest(@NotBlank String commandId) {
    }

    public record ReviewTaskRequest(
            @NotBlank String commandId,
            @NotNull ReviewConclusion conclusion,
            @Size(max = 500) String remark,
            @Size(max = 40) String reviewer) {
    }

    public record FaultInjectionRequest(
            @NotNull UUID taskId,
            @NotNull FaultType type,
            Boolean enabled) {
    }

    public record PointResponse(
            String id,
            String name,
            double positionMeters,
            String defaultInspectionItem) {
    }

    public record SegmentResponse(
            String id,
            String name,
            double lengthMeters,
            String description,
            List<PointResponse> inspectionPoints) {
    }

    public record DeviceResponse(
            String id,
            String name,
            DeviceType type,
            String state,
            boolean online,
            int batteryPercent,
            double positionMeters,
            UUID activeTaskId,
            String statusMessage,
            Instant updatedAt) {
    }

    public record DetectionResponse(
            UUID id,
            String inspectionPointId,
            double positionMeters,
            DetectionQuality quality,
            int captureIndex,
            Instant capturedAt,
            String imageUrl) {
    }

    public record ReviewResponse(
            ReviewConclusion conclusion,
            String remark,
            String reviewer,
            Instant reviewedAt) {
    }

    public record EventResponse(
            long id,
            TaskStatus fromStatus,
            TaskStatus toStatus,
            String eventType,
            String message,
            Instant createdAt) {
    }

    public record TaskSummaryResponse(
            UUID id,
            String taskCode,
            TaskSource source,
            String inspectionPointId,
            String inspectionPointName,
            String inspectionItem,
            TaskStatus status,
            String statusMessage,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt) {
    }

    public record TaskDetailResponse(
            TaskSummaryResponse task,
            PointResponse inspectionPoint,
            DetectionResponse latestDetection,
            ReviewResponse review,
            List<EventResponse> events) {
    }

    public record OperationResponse(String message, TaskDetailResponse task) {
    }
}
