package io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecordId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceState;

/**
 * Raised when a {@code RECONCILING} attendance record is missing its reconciliation anchor
 * ({@code reconciliation_started_at}). By construction {@code beginReconciliation} always snapshots
 * the authoritative {@code WorkshopCompleted.completedAt} when flipping {@code OPEN → RECONCILING}
 * (ADR 0019 §4), so a record in {@code RECONCILING} with a {@code null} anchor is corrupted/inconsistent
 * state — the domain refuses to proceed rather than guessing a deadline.
 *
 * <p>Local invariant violation (proven by a single aggregate's state) → lives in the domain exception
 * package; maps to HTTP 409 via the {@code AttendanceDomainException} handler.</p>
 */
public final class MissingReconciliationAnchorException extends AttendanceDomainException {

    private final AttendanceRecordId recordId;

    public MissingReconciliationAnchorException(AttendanceRecordId recordId) {
        super("Attendance record %s is %s but has no reconciliation anchor (reconciliationStartedAt is null)"
                .formatted(recordId.value(), AttendanceState.RECONCILING));
        this.recordId = recordId;
    }

    public AttendanceRecordId getRecordId() {
        return recordId;
    }
}
