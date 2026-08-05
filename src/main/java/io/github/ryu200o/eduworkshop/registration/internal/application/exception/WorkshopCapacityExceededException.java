package io.github.ryu200o.eduworkshop.registration.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationException;

import java.util.UUID;

/**
 * Application-layer exception raised when a student tries to book the last seat of a workshop that
 * has already reached its capacity.
 *
 * <p>The capacity rule is set-based (comparing a set of active registrations against the workshop's
 * capacity) and therefore orchestrated by the Application handler under the workshop lock-anchor
 * (ADR 0005 / ADR 0015) — it is never an aggregate-local invariant. Mapped to HTTP 409 CONFLICT,
 * consistent with the module's other business-conflict exceptions.</p>
 */
public final class WorkshopCapacityExceededException extends ApplicationException {

    public WorkshopCapacityExceededException(UUID workshopId, int capacity, int activeCount) {
        super("Workshop %s is full (capacity=%d, active=%d).".formatted(workshopId, capacity, activeCount));
    }
}
