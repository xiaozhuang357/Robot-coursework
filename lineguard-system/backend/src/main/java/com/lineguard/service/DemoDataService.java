package com.lineguard.service;

import com.lineguard.domain.Device;
import com.lineguard.domain.DomainTypes.DeviceType;
import com.lineguard.domain.DomainTypes.DockState;
import com.lineguard.domain.DomainTypes.RobotState;
import com.lineguard.domain.InspectionPoint;
import com.lineguard.domain.LineSegment;
import com.lineguard.repository.CommandReceiptRepository;
import com.lineguard.repository.DetectionRecordRepository;
import com.lineguard.repository.DeviceRepository;
import com.lineguard.repository.InspectionPointRepository;
import com.lineguard.repository.InspectionTaskRepository;
import com.lineguard.repository.LineSegmentRepository;
import com.lineguard.repository.ReviewRecordRepository;
import com.lineguard.repository.TaskEventRepository;
import com.lineguard.storage.InspectionImageStorage;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Order(1)
public class DemoDataService implements ApplicationRunner {

    public static final String SEGMENT_ID = "LG-SEG-001";
    public static final String ROBOT_ID = "LG-ROBOT-01";
    public static final String DOCK_ID = "LG-DOCK-01";

    private final LineSegmentRepository segmentRepository;
    private final InspectionPointRepository pointRepository;
    private final DeviceRepository deviceRepository;
    private final InspectionTaskRepository taskRepository;
    private final TaskEventRepository eventRepository;
    private final DetectionRecordRepository detectionRepository;
    private final ReviewRecordRepository reviewRepository;
    private final CommandReceiptRepository commandRepository;
    private final InspectionImageStorage imageStorage;

    public DemoDataService(LineSegmentRepository segmentRepository,
                           InspectionPointRepository pointRepository,
                           DeviceRepository deviceRepository,
                           InspectionTaskRepository taskRepository,
                           TaskEventRepository eventRepository,
                           DetectionRecordRepository detectionRepository,
                           ReviewRecordRepository reviewRepository,
                           CommandReceiptRepository commandRepository,
                           InspectionImageStorage imageStorage) {
        this.segmentRepository = segmentRepository;
        this.pointRepository = pointRepository;
        this.deviceRepository = deviceRepository;
        this.taskRepository = taskRepository;
        this.eventRepository = eventRepository;
        this.detectionRepository = detectionRepository;
        this.reviewRepository = reviewRepository;
        this.commandRepository = commandRepository;
        this.imageStorage = imageStorage;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedIfMissing();
    }

    @Transactional
    public void reset() {
        commandRepository.deleteAllInBatch();
        reviewRepository.deleteAllInBatch();
        detectionRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        taskRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
        pointRepository.deleteAllInBatch();
        segmentRepository.deleteAllInBatch();
        imageStorage.clear();
        seedIfMissing();
    }

    private void seedIfMissing() {
        if (!segmentRepository.existsById(SEGMENT_ID)) {
            segmentRepository.save(new LineSegment(SEGMENT_ID, "云岭示范输电区段", 1000,
                    "单档距非带电仿真线路，用于验证 LineGuard 核心巡检闭环"));
        }
        if (!pointRepository.existsById("LG-POINT-350")) {
            pointRepository.save(new InspectionPoint("LG-POINT-350", SEGMENT_ID,
                    "地线附件检测点 A", 350, "连接金具外观检查"));
        }
        if (!pointRepository.existsById("LG-POINT-720")) {
            pointRepository.save(new InspectionPoint("LG-POINT-720", SEGMENT_ID,
                    "重点复核点 B", 720, "地线表面与异物检查"));
        }
        if (!deviceRepository.existsById(ROBOT_ID)) {
            deviceRepository.save(new Device(ROBOT_ID, "LineGuard Robot 01", DeviceType.ROBOT,
                    RobotState.DOCKED.name(), true, 100, 0, "已驻留并完成充电确认"));
        }
        if (!deviceRepository.existsById(DOCK_ID)) {
            deviceRepository.save(new Device(DOCK_ID, "LineGuard Dock 01", DeviceType.DOCK,
                    DockState.LOCKED_CHARGING.name(), true, 100, 0, "机械锁止与补能状态正常"));
        }
    }
}
