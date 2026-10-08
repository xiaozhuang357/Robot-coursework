package com.lineguard.repository;

import com.lineguard.domain.DetectionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DetectionRecordRepository extends JpaRepository<DetectionRecord, UUID> {
    Optional<DetectionRecord> findFirstByTaskIdOrderByCapturedAtDesc(UUID taskId);
    long countByTaskId(UUID taskId);
}

