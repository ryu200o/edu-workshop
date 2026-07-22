package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;
import java.util.UUID;

public record WorkshopCreated(
        UUID workshopId,
        WorkshopId id,
        Instant startTime,
        Instant endTime,
        WorkshopCapacity capacity,
        Instant occurredAt
) implements WorkshopDomainEvent {
}
