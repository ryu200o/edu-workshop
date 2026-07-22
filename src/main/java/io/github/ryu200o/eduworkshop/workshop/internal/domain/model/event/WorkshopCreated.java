package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a workshop is first created (DRAFT). Carries the planning data
 * (time window, capacity) set at creation. The room is not yet assigned — that is a separate
 * {@link WorkshopScheduled} event.
 */
public record WorkshopCreated(
        UUID workshopId,
        WorkshopId id,
        Instant startTime,
        Instant endTime,
        WorkshopCapacity capacity,
        Instant occurredAt
) implements WorkshopDomainEvent {
}
