package io.github.ryu200o.eduworkshop.registration;

import java.util.List;
import java.util.UUID;

/**
 * Public inter-module communication interface for the registration module.
 * This is the only surface exposed to other modules.
 */
public interface RegistrationExposeAPI {

    /**
     * Counts the total active ({@code REGISTERED}) seats across multiple workshops.
     * Used by upper-layer modules (e.g. FacilityOps) to size the impact of a maintenance window.
     *
     * @param workshopIds the workshop ids to count registrations for
     * @return total number of active registrations across all specified workshops
     */
    int countActiveByWorkshopIds(List<UUID> workshopIds);
}
