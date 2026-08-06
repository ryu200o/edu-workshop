package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a PUBLISHED workshop is started ({@code Workshop.start} → IN_PROGRESS).
 * Carries the room id (always present at PUBLISHED — a publish-invariant) for downstream consumers
 * (e.g. attendance in Epic 3 / analytics in Epic 6). No integration event is mapped yet (YAGNI).
 */
public record WorkshopStarted(
        WorkshopId workshopId,
        UUID roomId,
        Instant occurredAt
) implements WorkshopDomainEvent {
}