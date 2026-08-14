package io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecordId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.StudentId;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: an attendance record was finalized once the Reconciliation Window closed
 * ({@code state → FINALIZED}). After this the record is permanently locked.
 */
public record AttendanceRecordFinalized(
        AttendanceRecordId recordId,
        StudentId studentId,
        UUID workshopId,
        Instant occurredAt
) implements AttendanceDomainEvent {
}