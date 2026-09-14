package com.lineguard.repository;

import com.lineguard.domain.TaskEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskEventRepository extends JpaRepository<TaskEvent, Long> {
    List<TaskEvent> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
    void deleteByTaskId(UUID taskId);
}

