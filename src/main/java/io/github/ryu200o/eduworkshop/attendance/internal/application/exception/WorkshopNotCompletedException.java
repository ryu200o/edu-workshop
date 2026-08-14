package io.github.ryu200o.eduworkshop.attendance.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationException;

import java.util.UUID;

/**
 * Application-layer exception raised when the workshop-roster finalize command runs while the
 * workshop is not yet {@code COMPLETED}. This is a system-initiated flow that must only run after
 * the workshop is completed (the Reconciliation Window is anchored to
 * {@code WorkshopCompleted.completedAt}, ADR 0019 §4) — so a non-{@code COMPLETED} workshop is a
 * scheduling/ordering error and we fail fast with a clear business exception instead of letting an
 * {@code OPEN} record blow up inside the domain with a confusing state error.
 *
 * <p>Global / set-based rule orchestrated at the Application edge (ADR 0005).</p>
 */
public final class WorkshopNotCompletedException extends ApplicationException {

    public WorkshopNotCompletedException(UUID workshopId, String currentState) {
        super("Workshop %s is not completed (state=%s); the roster can only be finalized after the "
                + "Reconciliation Window anchored to WorkshopCompleted.completedAt"
                .formatted(workshopId, currentState));
    }
}