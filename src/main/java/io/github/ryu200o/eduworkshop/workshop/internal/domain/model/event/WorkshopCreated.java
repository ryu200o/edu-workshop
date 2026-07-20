package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a workshop is first created (DRAFT).
 */
public record WorkshopCreated(
        UUID workshopId,
        WorkshopId id,
        Instant occurredAt
) implements WorkshopDomainEvent {
}
