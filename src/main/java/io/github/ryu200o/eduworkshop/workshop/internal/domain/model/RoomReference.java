package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

import java.util.UUID;

public record RoomReference(UUID roomId, String roomNameSnapshot, String roomLocationSnapshot, int roomCapacitySnapshot) {

    public RoomReference {
        if (roomId == null) {
            throw new IllegalArgumentException("Room reference must carry a room id.");
        }
        if (roomCapacitySnapshot < 0) {
            throw new IllegalArgumentException("Room capacity snapshot must be non-negative.");
        }
    }

    public static RoomReference of(UUID roomId, String roomNameSnapshot, String roomLocationSnapshot, int roomCapacitySnapshot) {
        return new RoomReference(roomId, roomNameSnapshot, roomLocationSnapshot, roomCapacitySnapshot);
    }
}
