package com.lineguard.repository;

import com.lineguard.domain.DomainTypes.TaskStatus;
import com.lineguard.domain.InspectionTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionTaskRepository extends JpaRepository<InspectionTask, UUID> {
    List<InspectionTask> findAllByOrderByCreatedAtDesc();
    Optional<InspectionTask> findFirstByStatusInOrderByCreatedAtDesc(Collection<TaskStatus> statuses);
}

