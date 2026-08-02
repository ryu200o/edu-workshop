package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;

/**
 * Domain event emitted when a pre-publish workshop's time window is updated
 * ({@link io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop#updateSchedule}).
 */
public record WorkshopScheduleUpdated(
        WorkshopId workshopId,
        Instant newStartTime,
        Instant newEndTime,
        Instant occurredAt
) implements WorkshopDomainEvent {
}