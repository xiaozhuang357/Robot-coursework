package com.lineguard.repository;

import com.lineguard.domain.CommandReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommandReceiptRepository extends JpaRepository<CommandReceipt, Long> {
    boolean existsByTaskIdAndCommandId(UUID taskId, String commandId);
}
