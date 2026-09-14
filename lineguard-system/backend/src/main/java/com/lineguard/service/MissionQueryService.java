package com.lineguard.service;

import com.lineguard.api.ApiDtos.DetectionResponse;
import com.lineguard.api.ApiDtos.DeviceResponse;
import com.lineguard.api.ApiDtos.EventResponse;
import com.lineguard.api.ApiDtos.PointResponse;
import com.lineguard.api.ApiDtos.ReviewResponse;
import com.lineguard.api.ApiDtos.SegmentResponse;
import com.lineguard.api.ApiDtos.TaskDetailResponse;
import com.lineguard.api.ApiDtos.TaskSummaryResponse;
import com.lineguard.api.BusinessException;
import com.lineguard.domain.DetectionRecord;
import com.lineguard.domain.Device;
import com.lineguard.domain.InspectionPoint;
import com.lineguard.domain.InspectionTask;
import com.lineguard.domain.LineSegment;
import com.lineguard.domain.ReviewRecord;
import com.lineguard.domain.TaskEvent;
import com.lineguard.repository.DetectionRecordRepository;
import com.lineguard.repository.DeviceRepository;
import com.lineguard.repository.InspectionPointRepository;
import com.lineguard.repository.InspectionTaskRepository;
import com.lineguard.repository.LineSegmentRepository;
import com.lineguard.repository.ReviewRecordRepository;
import com.lineguard.repository.TaskEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MissionQueryService {

    private final InspectionTaskRepository taskRepository;
    private final InspectionPointRepository pointRepository;
    private final LineSegmentRepository segmentRepository;
    private final DeviceRepository deviceRepository;
    private final TaskEventRepository eventRepository;
    private final DetectionRecordRepository detectionRepository;
    private final ReviewRecordRepository reviewRepository;

    public MissionQueryService(InspectionTaskRepository taskRepository,
                               InspectionPointRepository pointRepository,
                               LineSegmentRepository segmentRepository,
                               DeviceRepository deviceRepository,
                               TaskEventRepository eventRepository,
                               DetectionRecordRepository detectionRepository,
                               ReviewRecordRepository reviewRepository) {
        this.taskRepository = taskRepository;
        this.pointRepository = pointRepository;
        this.segmentRepository = segmentRepository;
        this.deviceRepository = deviceRepository;
        this.eventRepository = eventRepository;
        this.detectionRepository = detectionRepository;
        this.reviewRepository = reviewRepository;
    }

    public List<SegmentResponse> segments() {
        return segmentRepository.findAll().stream().map(this::mapSegment).toList();
    }

    public List<DeviceResponse> devices() {
        return deviceRepository.findAll().stream().map(this::mapDevice).toList();
    }

    public List<TaskSummaryResponse> tasks() {
        return taskRepository.findAllByOrderByCreatedAtDesc().stream().map(this::mapSummary).toList();
    }

    public Optional<TaskDetailResponse> activeTask() {
        return taskRepository.findFirstByStatusInOrderByCreatedAtDesc(
                com.lineguard.domain.DomainTypes.TaskStatus.activeStatuses()).map(this::mapDetail);
    }

    public TaskDetailResponse task(UUID taskId) {
        return mapDetail(taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "任务不存在")));
    }

    public DetectionRecord detection(UUID detectionId) {
        return detectionRepository.findById(detectionId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "检测记录不存在"));
    }

    private SegmentResponse mapSegment(LineSegment segment) {
        List<PointResponse> points = pointRepository.findByLineSegmentIdOrderByPositionMeters(segment.getId())
                .stream().map(this::mapPoint).toList();
        return new SegmentResponse(segment.getId(), segment.getName(), segment.getLengthMeters(),
                segment.getDescription(), points);
    }

    private DeviceResponse mapDevice(Device device) {
        return new DeviceResponse(device.getId(), device.getName(), device.getType(), device.getState(),
                device.isOnline(), device.getBatteryPercent(), device.getPositionMeters(),
                device.getActiveTaskId(), device.getStatusMessage(), device.getUpdatedAt());
    }

    private TaskSummaryResponse mapSummary(InspectionTask task) {
        InspectionPoint point = pointRepository.findById(task.getInspectionPointId()).orElse(null);
        return new TaskSummaryResponse(task.getId(), task.getTaskCode(), task.getSource(),
                task.getInspectionPointId(), point == null ? task.getInspectionPointId() : point.getName(),
                task.getInspectionItem(), task.getStatus(), task.getStatusMessage(), task.getCreatedAt(),
                task.getUpdatedAt(), task.getCompletedAt());
    }

    private TaskDetailResponse mapDetail(InspectionTask task) {
        InspectionPoint point = pointRepository.findById(task.getInspectionPointId())
                .orElseThrow(() -> new IllegalStateException("任务检测点不存在"));
        DetectionResponse detection = detectionRepository.findFirstByTaskIdOrderByCapturedAtDesc(task.getId())
                .map(this::mapDetection).orElse(null);
        ReviewResponse review = reviewRepository.findByTaskId(task.getId()).map(this::mapReview).orElse(null);
        List<EventResponse> events = eventRepository.findByTaskIdOrderByCreatedAtAsc(task.getId())
                .stream().map(this::mapEvent).toList();
        return new TaskDetailResponse(mapSummary(task), mapPoint(point), detection, review, events);
    }

    private PointResponse mapPoint(InspectionPoint point) {
        return new PointResponse(point.getId(), point.getName(), point.getPositionMeters(),
                point.getDefaultInspectionItem());
    }

    private DetectionResponse mapDetection(DetectionRecord record) {
        return new DetectionResponse(record.getId(), record.getInspectionPointId(), record.getPositionMeters(),
                record.getQuality(), record.getCaptureIndex(), record.getCapturedAt(),
                "/api/v1/detections/" + record.getId() + "/image");
    }

    private ReviewResponse mapReview(ReviewRecord record) {
        return new ReviewResponse(record.getConclusion(), record.getRemark(), record.getReviewer(),
                record.getReviewedAt());
    }

    private EventResponse mapEvent(TaskEvent event) {
        return new EventResponse(event.getId(), event.getFromStatus(), event.getToStatus(),
                event.getEventType(), event.getMessage(), event.getCreatedAt());
    }
}

