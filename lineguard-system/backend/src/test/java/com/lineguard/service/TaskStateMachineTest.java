package com.lineguard.service;

import com.lineguard.api.BusinessException;
import com.lineguard.domain.DomainTypes.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 状态机是任务闭环的守门人，它的"拒绝"能力比"允许"更容易被改坏：
 * 往转移表里多加一行不会让任何流程失败，却会让非法转换静默通过。
 * 这里针对拒绝路径单独断言。
 */
class TaskStateMachineTest {

    private final TaskStateMachine machine = new TaskStateMachine();

    @Test
    void allowsTransitionsThatAreOnTheWhitelist() {
        assertDoesNotThrow(() -> machine.check(TaskStatus.CREATED, TaskStatus.PRECHECKING));
        assertDoesNotThrow(() -> machine.check(TaskStatus.MOVING, TaskStatus.INSPECTING));
        assertDoesNotThrow(() -> machine.check(TaskStatus.SAFE_PARKED, TaskStatus.RETURNING));
    }

    @Test
    void rejectsTransitionOutsideTheWhitelist() {
        assertThrows(BusinessException.class,
                () -> machine.check(TaskStatus.CREATED, TaskStatus.COMPLETED));
        assertThrows(BusinessException.class,
                () -> machine.check(TaskStatus.INSPECTING, TaskStatus.COMPLETED));
    }

    @Test
    void terminalStatusesHaveNoOutgoingTransition() {
        assertThrows(BusinessException.class,
                () -> machine.check(TaskStatus.COMPLETED, TaskStatus.RETURNING));
        assertThrows(BusinessException.class,
                () -> machine.check(TaskStatus.ABORTED, TaskStatus.PRECHECKING));
    }
}
