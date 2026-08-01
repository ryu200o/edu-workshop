package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;

/**
 * Domain event emitted when a PUBLISHED workshop is cancelled ({@code Workshop.cancel}). The
 * Application layer maps this into the cross-module {@code WorkshopCancelledIntegrationEvent} so the
 * Registration module can flip all active seats to {@code CANCELLED} via the outbox.
 */
public record WorkshopCancelled(
        WorkshopId workshopId,
        Instant occurredAt
) implements WorkshopDomainEvent {
}
