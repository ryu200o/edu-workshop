package io.github.ryu200o.eduworkshop.attendance.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationException;

import java.util.List;
import java.util.UUID;

/**
 * Application-layer exception raised when one or more learners whose attendance is being marked do
 * not have a {@code VERIFIED} registration (SA directive — Registration Verification Dependency).
 * The gate is a global / set-based rule, so it is orchestrated here (ADR 0005); the
 * {@code AttendanceRecord} aggregate stays pure and knows nothing about Registration.
 *
 * <p>Mapped to HTTP 409 (business state conflict). The gate is fail-fast and atomic over the whole
 * batch: no learner is partially processed when any of them is not verified.</p>
 */
public final class RegistrationNotVerifiedException extends ApplicationException {

    private final List<UUID> studentIds;

    public RegistrationNotVerifiedException(UUID workshopId, List<UUID> studentIds) {
        super("Attendance for workshop %s rejected: registration not verified for student(s) %s"
                .formatted(workshopId, studentIds));
        this.studentIds = List.copyOf(studentIds);
    }

    public List<UUID> studentIds() {
        return studentIds;
    }
}