package io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecordId;

/**
 * Raised when a mutation is attempted on an attendance record whose Reconciliation Window has
 * closed ({@code FINALIZED}). The record is permanently locked.
 */
public final class AttendanceRecordFinalizedException extends AttendanceDomainException {

    public AttendanceRecordFinalizedException(AttendanceRecordId recordId) {
        super("Attendance record is finalized and locked: " + recordId.value());
    }

    public AttendanceRecordFinalizedException(String message) {
        super(message);
    }
}