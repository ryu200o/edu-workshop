package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;

/**
 * Domain event emitted when a workshop is published (SCHEDULED → PUBLISHED). At this point the Application
 * layer has already confirmed the room is free (no other PUBLISHED workshop owns the window), so the room
 * is now reserved. The event carries no payload beyond identity/timing; the reserved window is already on
 * the aggregate.
 */
public record WorkshopPublished(
        WorkshopId workshopId,
        Instant occurredAt
) implements WorkshopDomainEvent {
}
