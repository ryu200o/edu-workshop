package io.github.ryu200o.eduworkshop.workshop.contract.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Sealed marker for cross-module integration events emitted by the Workshop module. Only events that
 * have an actual consumer in another module are published (YAGNI) — currently just
 * {@link WorkshopCancelledIntegrationEvent}, consumed by Registration to flip active seats.
 */
public sealed interface WorkshopIntegrationEvent
        permits WorkshopCancelledIntegrationEvent {

    UUID workshopId();

    Instant occurredAt();
}
