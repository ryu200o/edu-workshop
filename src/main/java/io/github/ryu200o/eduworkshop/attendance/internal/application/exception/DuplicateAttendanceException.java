package io.github.ryu200o.eduworkshop.attendance.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationException;

import java.util.UUID;

/**
 * Application-layer exception raised when a second attendance record is attempted for the same
 * (workshop, student) pair.
 *
 * <p>This is a <em>global / set-based</em> rule (ADR 0005) — it cannot be proven by a single
 * aggregate — so it is orchestrated by the Application handler (fast-fail read) and backed by the
 * DB unique index {@code uq_student_workshop} (race-proof backstop): the write adapter translates a
 * {@code DataIntegrityViolationException} into this exception.</p>
 */
public final class DuplicateAttendanceException extends ApplicationException {

    public DuplicateAttendanceException(UUID workshopId, UUID studentId) {
        super("An attendance record already exists for student %s in workshop %s"
                .formatted(studentId, workshopId));
    }
}