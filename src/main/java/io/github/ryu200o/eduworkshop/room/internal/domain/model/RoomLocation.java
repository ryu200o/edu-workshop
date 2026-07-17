package io.github.ryu200o.eduworkshop.room.internal.domain.model;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Atomic Value Object representing a room's physical location.
 *
 * <p>Composed of an immutable {@code building} (alphanumeric block name) and a positive integer
 * {@code floor}. Instances self-normalize on construction (trim + uppercase building) so that
 * equality and comparison are exact and independent of how the raw input was formatted.</p>
 */
public final class RoomLocation {

    private final String building;
    private final int floor;

    private RoomLocation(String building, int floor) {
        this.building = building;
        this.floor = floor;
    }

    public static @NonNull RoomLocation of(String building, int floor) {
        String normalized = normalizeBuilding(building);
        if (floor <= 0) {
            throw new RoomDomainException("Room floor must be a positive integer.");
        }
        return new RoomLocation(normalized, floor);
    }

    /**
     * Reconstructs a location from persisted, already-normalized parts (no re-validation of format).
     */
    public static @NonNull RoomLocation reconstruct(String building, int floor) {
        return new RoomLocation(building, floor);
    }

    private static @NonNull String normalizeBuilding(String building) {
        if (building == null || building.isBlank()) {
            throw new RoomDomainException("Room building must not be blank.");
        }
        String trimmed = building.trim().toUpperCase();
        if (trimmed.contains(".")) {
            throw new RoomDomainException("Room building must not contain a dot.");
        }
        return trimmed;
    }

    public String building() {
        return building;
    }

    public int floor() {
        return floor;
    }

    public @NonNull String asString() {
        return building + " / Floor " + floor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof RoomLocation that
                && floor == that.floor
                && building.equals(that.building);
    }

    @Override
    public int hashCode() {
        return Objects.hash(building, floor);
    }

    @Override
    public String toString() {
        return asString();
    }
}
