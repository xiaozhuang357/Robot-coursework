package com.lineguard.domain;

import com.lineguard.domain.DomainTypes.CommandType;
import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "command_receipts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "command_id"}))
public class CommandReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "task_id")
    private UUID taskId;
    @Column(name = "command_id")
    private String commandId;
    @Enumerated(EnumType.STRING)
    private CommandType commandType;
    private Instant receivedAt;

    protected CommandReceipt() {
    }

    public CommandReceipt(UUID taskId, String commandId, CommandType commandType) {
        this.taskId = taskId;
        this.commandId = commandId;
        this.commandType = commandType;
        this.receivedAt = Instant.now();
    }

    public Long getId() { return id; }
}
