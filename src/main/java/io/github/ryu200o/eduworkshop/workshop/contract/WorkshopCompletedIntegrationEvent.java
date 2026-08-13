package io.github.ryu200o.eduworkshop.workshop.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-module integration event emitted (via the outbox) when an IN_PROGRESS workshop is completed
 * ({@code → COMPLETED}). The Attendance module consumes it to open the Reconciliation Window on every
 * non-finalized attendance record: {@code beginReconciliation(completedAt)} anchors
 * {@code reconciliation_started_at} to {@code completedAt} — the authoritative temporal anchor,
 * never inferred from the consumer's clock (ADR 0019 §4).
 */
public record WorkshopCompletedIntegrationEvent(
        UUID workshopId,
        Instant completedAt
) implements WorkshopIntegrationEvent {

    @Override
    public Instant occurredAt() {
        return completedAt;
    }
}