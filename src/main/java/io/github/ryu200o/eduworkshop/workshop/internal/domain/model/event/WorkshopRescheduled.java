package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;

/**
 * Domain event emitted when a PUBLISHED workshop is rescheduled ({@code Workshop.reschedule}): the
 * time window changes while the room and the student registrations are kept. Carries both the old
 * and the new window so consumers (e.g. the Registration module refreshing its
 * {@code workshop_start_time} snapshot) can react without re-querying the Workshop module.
 */
public record WorkshopRescheduled(
        WorkshopId workshopId,
        Instant oldStartTime,
        Instant oldEndTime,
        Instant newStartTime,
        Instant newEndTime,
        Instant occurredAt
) implements WorkshopDomainEvent {
}
