package io.github.ryu200o.eduworkshop.attendance.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationException;

/**
 * Application-layer exception raised when attendance is marked for a workshop that is not
 * {@code IN_PROGRESS}. Per ADR 0019 §3, {@code Workshop.state} is the authority for the attendance
 * lifecycle — the domain never infers it from time. The gate is a global rule orchestrated here
 * (ADR 0005).
 */
public final class WorkshopNotInSessionException extends ApplicationException {

    public WorkshopNotInSessionException(java.util.UUID workshopId, String currentState) {
        super("Workshop %s is not in session (state=%s); attendance can only be marked while IN_PROGRESS"
                .formatted(workshopId, currentState));
    }
}