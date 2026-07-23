package io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.view;

import java.time.Instant;
import java.util.UUID;

public record WorkshopDetailView(
        UUID id,
        String title,
        String description,
        UUID roomId,
        String roomNameSnapshot,
        String roomLocationSnapshot,
        Integer roomCapacitySnapshot,
        boolean hasRoomWarning,
        Instant startTime,
        Instant endTime,
        int capacity,
        String state,
        Instant createdAt,
        Instant updatedAt
) {
}
