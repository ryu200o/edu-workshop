package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;

/**
 * Domain event emitted when a PUBLISHED workshop's capacity is adjusted
 * ({@code Workshop.adjustCapacity}). Carries the new {@link WorkshopCapacity} so consumers can
 * react without re-querying the aggregate.
 */
public record WorkshopCapacityAdjusted(
        WorkshopId workshopId,
        WorkshopCapacity newCapacity,
        Instant occurredAt
) implements WorkshopDomainEvent {
}
