package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

import java.util.UUID;

/**
 * Cross-module value object carrying a reference to a Room aggregate. The Workshop module holds a
 * logical {@code roomId} UUID plus selective denormalized snapshots ({@code roomNameSnapshot},
 * {@code roomLocationSnapshot}) to avoid physical foreign keys and cross-module JOINs
 * (per ADR 0007 — selective snapshotting). The snapshots are populated proactively from
 * {@code RoomExposeAPI}; reactive sync via Room events is deferred.
 */
public record RoomReference(UUID roomId, String roomNameSnapshot, String roomLocationSnapshot) {

    public RoomReference {
        if (roomId == null) {
            throw new IllegalArgumentException("Room reference must carry a room id.");
        }
    }

    public static RoomReference of(UUID roomId, String roomNameSnapshot, String roomLocationSnapshot) {
        return new RoomReference(roomId, roomNameSnapshot, roomLocationSnapshot);
    }
}
