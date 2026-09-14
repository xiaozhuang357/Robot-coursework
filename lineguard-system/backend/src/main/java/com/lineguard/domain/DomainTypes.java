package com.lineguard.domain;

import java.util.EnumSet;
import java.util.Set;

public final class DomainTypes {

    private DomainTypes() {
    }

    public enum DeviceType {
        ROBOT, DOCK
    }

    public enum RobotState {
        DOCKED, UNDOCKING, MOVING_OUTBOUND, INSPECTING, WAITING_REVIEW,
        MOVING_RETURN, DOCKING, SAFE_PARKED, FAULT
    }

    public enum DockState {
        LOCKED_CHARGING, RELEASING, WAITING_RETURN, DOCKING, FAULT
    }

    public enum TaskStatus {
        CREATED, PRECHECKING, DEPARTING, MOVING, INSPECTING, UPLOADING,
        AWAITING_REVIEW, RETURNING, DOCKING, COMPLETED,
        PRECHECK_FAILED, WAITING_MANUAL, SAFE_PARKED, ABORTED;

        public boolean isTerminal() {
            return this == COMPLETED || this == ABORTED;
        }

        public boolean pausesSimulation() {
            return this == AWAITING_REVIEW || this == PRECHECK_FAILED
                    || this == WAITING_MANUAL || this == SAFE_PARKED || isTerminal();
        }

        public static Set<TaskStatus> activeStatuses() {
            return EnumSet.of(PRECHECKING, DEPARTING, MOVING, INSPECTING, UPLOADING,
                    AWAITING_REVIEW, RETURNING, DOCKING, WAITING_MANUAL, SAFE_PARKED);
        }
    }

    public enum TaskSource {
        MANUAL, PERIODIC, EXTERNAL_ALERT
    }

    public enum DetectionQuality {
        READABLE, UNREADABLE
    }

    public enum ReviewConclusion {
        NO_ABNORMALITY, CONTINUE_OBSERVATION, SPECIAL_REINSPECTION, DISPATCH_PERSONNEL
    }

    public enum FaultType {
        LOW_BATTERY, COMMUNICATION_LOSS, OBSTACLE, DOCKING_FAILURE, BLURRED_IMAGE
    }

    public enum CommandType {
        DISPATCH, REVIEW_AND_RETURN, RETURN, ABORT, RECAPTURE
    }
}

