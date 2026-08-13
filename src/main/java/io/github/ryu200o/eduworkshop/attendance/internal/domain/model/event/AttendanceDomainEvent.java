package io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event;

/**
 * Sealed marker for domain events emitted by the {@code AttendanceRecord} aggregate. Each event
 * represents a business fact on the Decision Ledger (ADR 0019 §11) and is published through the
 * transactional outbox (ADR 0011).
 */
public sealed interface AttendanceDomainEvent
        permits AttendanceMarked,
                AuditorAdjustedAttendance,
                AttendanceRecordFinalized,
                AttendanceAppealSubmitted {
}