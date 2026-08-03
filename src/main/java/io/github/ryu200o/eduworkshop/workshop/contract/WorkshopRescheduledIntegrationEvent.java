package io.github.ryu200o.eduworkshop.workshop.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-module integration event emitted (via the outbox) when a PUBLISHED workshop is rescheduled.
 * The Registration module consumes it to refresh the {@code workshop_start_time} snapshot of every
 * active seat while keeping the {@code REGISTERED} status (ADR 0007/0012).
 */
public record WorkshopRescheduledIntegrationEvent(
        UUID workshopId,
        Instant oldStartTime,
        Instant oldEndTime,
        Instant newStartTime,
        Instant newEndTime,
        Instant occurredAt
) implements WorkshopIntegrationEvent {
}
