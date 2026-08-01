package io.github.ryu200o.eduworkshop.registration;

import java.util.UUID;

/**
 * Public inter-module communication interface for the registration module.
 * This is the only surface exposed to other modules.
 */
public interface RegistrationExposeAPI {

    /**
     * Counts the active ({@code REGISTERED}) seats taken for a workshop. This is the "anchor"
     * number the Workshop module uses (Phase 2) to validate post-publish changes — cancelling the
     * workshop, lowering its capacity, or moving it to another room.
     */
    int countActiveRegistrations(UUID workshopId);
}
