package io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecordId;

import java.time.Instant;

/**
 * Raised when a student appeal is submitted after the Reconciliation Window has closed
 * ({@code now > reconciliationDeadline}). Mapped to HTTP 409 (business state conflict).
 */
public final class ReconciliationWindowExceededException extends AttendanceDomainException {

    private final AttendanceRecordId recordId;
    private final Instant reconciliationDeadline;
    private final Instant attemptedAt;

    public ReconciliationWindowExceededException(AttendanceRecordId recordId,
                                                 Instant reconciliationDeadline,
                                                 Instant attemptedAt) {
        super("Reconciliation window for attendance record %s closed at %s; attempted at %s"
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