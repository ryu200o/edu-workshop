package io.github.ryu200o.eduworkshop.room.internal.application.port.in.query;

import java.util.UUID;

/**
 * Lightweight read projection (View) summarizing a room — a flattened subset of {@link RoomDetailView}
 * for list/lookup-by-name contexts. Lives on the Query side; allowed to grow or shrink independently
 * of the write flow (CQRS bypass).
 *
 * @param id       the room id
 * @param name     the canonical room name (e.g. "F.0201")
 * @param building the building/block
 * @param floor    the floor number
 */
public record RoomSummaryView(
        UUID id,
        String name,
        String building,
        int floor
) {
}
