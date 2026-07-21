package io.github.ryu200o.eduworkshop.room.internal.domain.model;

public record RoomLocation(String building, int floor) {

    public RoomLocation {
        if (building == null || building.isBlank()) {
            throw new IllegalArgumentException("Room building must not be blank.");
        }
        if (building.contains(".")) {
            throw new IllegalArgumentException("Room building must not contain a dot.");
        }
        if (floor <= 0) {
            throw new IllegalArgumentException("Room floor must be a positive integer.");
        }
    }

    public static RoomLocation of(String building, int floor) {
        String normalized = (building == null) ? null : building.trim().toUpperCase();
        return new RoomLocation(normalized, floor);
    }

    public String asString() {
        return building + " / Floor " + floor;
    }
}
