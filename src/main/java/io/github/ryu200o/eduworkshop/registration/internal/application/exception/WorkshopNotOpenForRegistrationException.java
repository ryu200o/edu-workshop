package io.github.ryu200o.eduworkshop.registration.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationException;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopStateContract;

import java.util.UUID;

/**
 * Application-layer exception raised when a student tries to register for a workshop that is not
 * open for booking.
 *
 * <p>Per the SA+PO decision, seats are only sold/opened once a workshop is {@code PUBLISHED};
 * {@code DRAFT}/{@code PLANNED} are still planning stages. This is a cross-aggregate rule
 * orchestrated by the Application handler (ADR 0005), not a domain invariant.</p>
 */
public final class WorkshopNotOpenForRegistrationException extends ApplicationException {

    public WorkshopNotOpenForRegistrationException(UUID workshopId, WorkshopStateContract state) {
        super("Workshop %s is not open for registration (current state: %s)".formatted(workshopId, state));
    }
}
