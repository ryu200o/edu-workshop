package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;

/**
 * Domain event emitted when an IN_PROGRESS workshop is completed ({@code Workshop.complete} → COMPLETED).
 * No Application layer maps this to an integration event yet (YAGNI).
 */
public record WorkshopCompleted(
        WorkshopId workshopId,
        Instant occurredAt
) implements WorkshopDomainEvent {
}