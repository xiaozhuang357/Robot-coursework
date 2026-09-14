package com.lineguard.repository;

import com.lineguard.domain.ReviewRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRecordRepository extends JpaRepository<ReviewRecord, UUID> {
    Optional<ReviewRecord> findByTaskId(UUID taskId);
}

