package io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecordId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.StudentId;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a student submitted an appeal (request + evidence) during the Reconciliation
 * Window. An appeal does <em>not</em> change {@code currentResult} — it only records the request
 * on the ledger; only {@code auditorAdjust} is an authoritative mutation (ADR 0019 §5).
 */
public record AttendanceAppealSubmitted(
        AttendanceRecordId recordId,
        StudentId studentId,
        UUID workshopId,
        String reason,
        Instant occurredAt
) implements AttendanceDomainEvent {
}