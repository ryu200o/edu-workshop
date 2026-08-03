package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a PUBLISHED workshop receives an eviction notice because a room
 * maintenance window now overlaps its time slot ({@code Workshop.markRoomEvicted}). The workshop's
 * state is NOT changed — it stays {@code PUBLISHED} — but it is flagged with {@code isRoomEvicted}
 * so the organizer can react. The {@code roomId} is the workshop's assigned room (a plain
 * {@link UUID}: the Workshop module deliberately does not import Room's {@code RoomId} VO across the
 * module boundary, ADR 0010). Domain-internal only — no cross-module integration event is published
 * for it (YAGNI: no consumer).
 */
public record WorkshopRoomEvicted(
        WorkshopId workshopId,
        UUID roomId,
        Instant occurredAt
) implements WorkshopDomainEvent {
}
