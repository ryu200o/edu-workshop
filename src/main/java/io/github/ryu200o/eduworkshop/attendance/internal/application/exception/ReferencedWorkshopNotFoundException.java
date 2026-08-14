package io.github.ryu200o.eduworkshop.attendance.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ResourceNotFoundException;

/**
 * Application-layer exception raised when a referenced workshop cannot be found via
 * {@code WorkshopExposeAPI.getScheduling}. Thrown by attendance handlers after an empty facade
 * lookup. This is an application concern, not a domain invariant.
 */
public final class ReferencedWorkshopNotFoundException extends ResourceNotFoundException {

    public ReferencedWorkshopNotFoundException(java.util.UUID workshopId) {
        super("Workshop", "id", workshopId);
    }
}