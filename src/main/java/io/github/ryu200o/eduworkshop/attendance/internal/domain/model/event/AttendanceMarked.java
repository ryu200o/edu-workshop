package io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecordId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.StudentId;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a learner's attendance was marked (or corrected) by the trainer while the workshop
 * was {@code IN_PROGRESS}.
 */
public record AttendanceMarked(
        AttendanceRecordId recordId,
        StudentId studentId,
        UUID workshopId,
        AttendanceResult result,
        Instant occurredAt
) implements AttendanceDomainEvent {
}