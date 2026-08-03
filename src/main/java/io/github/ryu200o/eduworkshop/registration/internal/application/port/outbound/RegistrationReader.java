package io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound;

import java.util.List;
import java.util.UUID;

/**
 * Read-side outbound port (SPI) for the Registration read side. Consumer-Driven: it declares only
 * the lookups the query use cases / Module Facade actually need. Returns primitives / projections
 * directly (CQRS bypass — no domain aggregate reconstruction). Implementations must be side-effect
 * free.
 */
public interface RegistrationReader {

    /**
     * Counts the active ({@code REGISTERED}) seats taken for a workshop. This is the "anchor"
     * number Phase 2 uses to validate post-publish changes (cancelling the workshop, lowering the
     * capacity, changing the room).
     */
    int countActiveByWorkshop(UUID workshopId);

    /**
     * Counts the total active ({@code REGISTERED}) seats across multiple workshops.
     * Used by the Impact Preview query to determine how many students are affected
     * by a maintenance window.
     *
     * @param workshopIds the workshop ids to count registrations for
     * @return total number of active registrations across all specified workshops
     */
    int countActiveByWorkshopIds(List<UUID> workshopIds);
}
