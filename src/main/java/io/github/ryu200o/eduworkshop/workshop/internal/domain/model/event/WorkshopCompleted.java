package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;

/**
 * Domain event emitted when an IN_PROGRESS workshop is completed ({@code Workshop.complete} → COMPLETED).
 * The Application layer maps this into {@code WorkshopCompletedIntegrationEvent} so the Attendance
 * module can open the Reconciliation Window (ADR 0019 §4).
 */
public record WorkshopCompleted(
        WorkshopId workshopId,
        Instant occurredAt
) implements WorkshopDomainEvent {
}