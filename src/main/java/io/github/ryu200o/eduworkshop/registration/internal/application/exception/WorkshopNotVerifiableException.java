package io.github.ryu200o.eduworkshop.registration.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationException;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopStateContract;

import java.util.UUID;

/**
 * Application-layer exception raised when a verifier tries to verify a ticket for a workshop that is
 * not open for verification (Epic 3C, OQ-3C-2). Only {@code PUBLISHED} and {@code IN_PROGRESS}
 * workshops may have their tickets verified; {@code DRAFT}/{@code PLANNED} are still planning stages
 * and {@code CANCELLED}/{@code COMPLETED} are closed. This is a cross-module rule orchestrated by
 * the Application handler via the Workshop facade (ADR 0005 / ADR 0010), not a domain invariant.
 */
public final class WorkshopNotVerifiableException extends ApplicationException {

    public WorkshopNotVerifiableException(UUID workshopId, WorkshopStateContract state) {
        super("Workshop %s is not open for verification (current state: %s)".formatted(workshopId, state));
    }
}