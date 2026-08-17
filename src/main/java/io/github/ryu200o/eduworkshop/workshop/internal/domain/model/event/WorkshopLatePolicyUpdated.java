package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;

/**
 * Domain event emitted when a workshop's attendance late-policy threshold is changed
 * (Epic 3C — the Workshop owns the attendance policy, ADR 0019 §13.1).
 *
 * <p>Mutable in {@code DRAFT}/{@code PLANNED}/{@code PUBLISHED}; frozen from {@code IN_PROGRESS}
 * (domain invariant, {@code InvalidWorkshopStateException}). The threshold is evaluated live at
 * check-in time (OQ-3C-10) — the Attendance module consumes {@code evaluateCheckIn} and never
 * snapshots the policy.</p>
 */
public record WorkshopLatePolicyUpdated(
        WorkshopId workshopId,
        int lateThresholdSeconds,
        Instant updatedAt
) implements WorkshopDomainEvent {
}