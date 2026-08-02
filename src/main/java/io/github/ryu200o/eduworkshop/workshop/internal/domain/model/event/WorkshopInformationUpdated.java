package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;

/**
 * Domain event emitted when a workshop's title and/or description is updated
 * ({@link io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop#updateInformation}).
 */
public record WorkshopInformationUpdated(
        WorkshopId workshopId,
        String newTitle,
        String newDescription,
        Instant occurredAt
) implements WorkshopDomainEvent {
}