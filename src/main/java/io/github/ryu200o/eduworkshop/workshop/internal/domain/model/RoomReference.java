package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

import java.util.UUID;

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
