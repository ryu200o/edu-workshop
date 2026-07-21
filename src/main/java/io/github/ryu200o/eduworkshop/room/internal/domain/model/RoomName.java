package io.github.ryu200o.eduworkshop.room.internal.domain.model;

public record RoomName(String value) {

    public RoomName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Room name must not be blank.");
        }
    }

    public static RoomName of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Room name must not be blank.");
        }
        return new RoomName(raw.trim().toUpperCase());
    }
}
