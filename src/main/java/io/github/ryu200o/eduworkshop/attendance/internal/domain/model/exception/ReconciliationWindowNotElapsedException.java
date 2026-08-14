package io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecordId;

import java.time.Instant;

/**
 * Raised when an attendance record is finalized before its Reconciliation Window has elapsed
 * ({@code now < reconciliationDeadline}). The window is anchored to
 * {@code reconciliationStartedAt + window}; finalization is only allowed at or after the deadline.
 */
public final class ReconciliationWindowNotElapsedException extends AttendanceDomainException {

    private final AttendanceRecordId recordId;
    private final Instant reconciliationDeadline;
    private final Instant attemptedAt;

    public ReconciliationWindowNotElapsedException(AttendanceRecordId recordId,
                                                   Instant reconciliationDeadline,
                                                   Instant attemptedAt) {
        super("Reconciliation window for attendance record %s has not elapsed yet; closes at %s, attempted at %s"
                .formatted(recordId.value(), reconciliationDeadline, attemptedAt));
        this.recordId = recordId;
        this.reconciliationDeadline = reconciliationDeadline;
        this.attemptedAt = attemptedAt;
    }

    public AttendanceRecordId getRecordId() {
        return recordId;
    }

    public Instant getReconciliationDeadline() {
        return reconciliationDeadline;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}