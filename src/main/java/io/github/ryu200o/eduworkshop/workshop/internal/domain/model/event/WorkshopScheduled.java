package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;

public record WorkshopScheduled(
        WorkshopId workshopId,
        RoomReference roomReference,
        Instant occurredAt
) implements WorkshopDomainEvent {
}
