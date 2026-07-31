package io.github.ryu200o.eduworkshop.workshop;

import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopRegistrationContract;

import java.util.Optional;
import java.util.UUID;

/**
 * Public inter-module communication interface for the workshop module.
 * This is the only surface exposed to other modules.
 */
public interface WorkshopExposeAPI {

    /**
     * Returns the minimal workshop snapshot needed for registration: its state (a workshop is open
     * for booking only when {@code PUBLISHED}) and its start time (used by Registration to enforce
     * its own cancellation-deadline invariant). Empty when the workshop does not exist.
     */
    Optional<WorkshopRegistrationContract> findForRegistration(UUID workshopId);
}
