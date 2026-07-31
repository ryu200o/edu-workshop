package io.github.ryu200o.eduworkshop.registration.internal.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Decoupled reference to the workshop a registration belongs to.
 *
 * <p>Carries the logical {@code workshopId} plus a <em>selective snapshot</em> of the workshop's
 * {@code startTime} (tinh thần ADR 0007). The snapshot lets the Registration bounded context enforce
 * its own cancellation-deadline invariant autonomously — no temporal coupling to the Workshop module
 * at cancellation time. The snapshot is refreshed when the workshop is rescheduled (Registration
 * listens to {@code WorkshopRescheduled} via the outbox in a later phase).</p>
 *
 * <p>This is the Registration module's own VO; it never imports Workshop {@code internal} types
 * (per ADR 0010). The Application layer maps a {@code WorkshopExposeAPI} contract DTO into this VO.</p>
 */
public record WorkshopReference(UUID workshopId, Instant startTime) {

    public WorkshopReference {
        if (workshopId == null) {
            throw new IllegalArgumentException("workshopId must not be null.");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("startTime must not be null.");
        }
    }

    public static WorkshopReference of(UUID workshopId, Instant startTime) {
        return new WorkshopReference(workshopId, startTime);
    }
}
