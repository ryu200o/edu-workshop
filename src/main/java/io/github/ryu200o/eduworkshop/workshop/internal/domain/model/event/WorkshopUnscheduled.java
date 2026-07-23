package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;

public record WorkshopUnscheduled(
        WorkshopId workshopId,
        Instant occurredAt
) implements WorkshopDomainEvent {
}
