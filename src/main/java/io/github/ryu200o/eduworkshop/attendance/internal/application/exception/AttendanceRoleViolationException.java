package io.github.ryu200o.eduworkshop.attendance.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationException;

/**
 * Application-layer exception raised when an actor attempts an attendance decision their role does
 * not allow (the State Matrix, ADR 0019 §9). Role authorization is enforced by the Application layer
 * from the authenticated principal (ADR 0019 §8). Mapped to HTTP 403.
 */
public final class AttendanceRoleViolationException extends ApplicationException {

    public AttendanceRoleViolationException(String role, String operation) {
        super("Role '%s' is not allowed to %s".formatted(role, operation));
    }
}