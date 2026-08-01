package io.github.ryu200o.eduworkshop.workshop.contract.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-module integration event emitted (via the outbox) when a PUBLISHED workshop is cancelled.
 * The Registration module consumes it to flip every active seat ({@code REGISTERED}) for that
 * workshop to {@code CANCELLED} regardless of the 24-hour cancellation deadline.
 */
public record WorkshopCancelledIntegrationEvent(
        UUID workshopId,
        Instant occurredAt
) implements WorkshopIntegrationEvent {
}
