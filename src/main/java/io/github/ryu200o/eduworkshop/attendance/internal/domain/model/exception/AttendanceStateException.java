package io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecordId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceState;

/**
 * Raised when a ledger decision is attempted in the wrong {@link AttendanceState} (the State Matrix,
 * ADR 0019 §9). Examples: marking attendance while the record is {@code RECONCILING}, or appealing
 * while the record is still {@code OPEN}.
 */
public final class AttendanceStateException extends AttendanceDomainException {

    private final AttendanceRecordId recordId;
    private final AttendanceState currentState;
    private final AttendanceState[] allowedStates;

    public AttendanceStateException(AttendanceRecordId recordId,
                                    AttendanceState currentState,
                                    AttendanceState... allowedStates) {
        super("Cannot perform this decision on attendance record %s in state %s; allowed: %s"
                .formatted(recordId.value(), currentState, java.util.Arrays.toString(allowedStates)));
        this.recordId = recordId;
        this.currentState = currentState;
        this.allowedStates = allowedStates;
    }

    public AttendanceRecordId getRecordId() {
        return recordId;
    }

    public AttendanceState getCurrentState() {
        return currentState;
    }

    public AttendanceState[] getAllowedStates() {
        return allowedStates;
    }
}