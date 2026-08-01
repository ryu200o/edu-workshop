package io.github.ryu200o.eduworkshop.workshop.contract.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Sealed marker for cross-module integration events emitted by the Workshop module. Only events that
 * have an actual consumer in another module are published (YAGNI) — currently
 * {@link WorkshopCancelledIntegrationEvent} (Registration flips active seats) and
 * {@link WorkshopRescheduledIntegrationEvent} (Registration refreshes its start-time snapshot).
 */
public sealed interface WorkshopIntegrationEvent
        permits WorkshopCancelledIntegrationEvent,
                WorkshopRescheduledIntegrationEvent {

    UUID workshopId();

    Instant occurredAt();
}
