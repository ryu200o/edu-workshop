package io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecordId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.StudentId;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: an auditor adjusted the attendance outcome during Reconciliation — the only
 * authoritative mutation of {@code currentResult} in the RECONCILING window (ADR 0019 §5).
 */
public record AuditorAdjustedAttendance(
        AttendanceRecordId recordId,
        StudentId studentId,
        UUID workshopId,
        AttendanceResult result,
        Instant occurredAt
) implements AttendanceDomainEvent {
}