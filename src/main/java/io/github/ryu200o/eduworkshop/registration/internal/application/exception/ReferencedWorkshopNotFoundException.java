package io.github.ryu200o.eduworkshop.registration.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ResourceNotFoundException;

/**
 * Application-layer exception raised when the referenced workshop cannot be found while registering.
 * Thrown by the register handler after {@code WorkshopExposeAPI.lockForRegistration} returns empty.
 * This is an application concern, not a domain invariant.
 */
public final class ReferencedWorkshopNotFoundException extends ResourceNotFoundException {

    public ReferencedWorkshopNotFoundException(java.util.UUID workshopId) {
        super("Workshop", "id", workshopId);
    }
}
