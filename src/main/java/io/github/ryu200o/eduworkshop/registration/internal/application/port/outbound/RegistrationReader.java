package io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound;

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
}
