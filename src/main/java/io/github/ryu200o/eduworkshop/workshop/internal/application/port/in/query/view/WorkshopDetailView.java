package io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.view;

import java.time.Instant;
import java.util.UUID;

/**
 * Read projection (View) for a single workshop's full detail. Lives on the Query side and is allowed
 * to aggregate/flatten data freely for the consumer, without any coupling to the write flow. Assembled
 * directly by the read adapter (CQRS bypass — no domain aggregate reconstruction).
 *
 * @param id                   the workshop id
 * @param title                the workshop title
 * @param description          the workshop description (nullable)
 * @param roomId               the assigned room id (nullable before scheduling)
 * @param roomNameSnapshot     the denormalized room name (nullable before scheduling)
 * @param roomLocationSnapshot the denormalized room location (nullable before scheduling)
 * @param startTime            the planned start time
 * @param endTime              the planned end time
 * @param capacity             the maximum participant count
 * @param state                the lifecycle state (DRAFT / SCHEDULED / PUBLISHED / …)
 * @param createdAt            the creation timestamp
 * @param updatedAt            the last update timestamp
 */
public record WorkshopDetailView(
        UUID id,
        String title,
        String description,
        UUID roomId,
        String roomNameSnapshot,
        String roomLocationSnapshot,
        Instant startTime,
        Instant endTime,
        int capacity,
        String state,
        Instant createdAt,
        Instant updatedAt
) {
}
