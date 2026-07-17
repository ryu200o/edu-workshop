package io.github.ryu200o.eduworkshop.room.internal.domain.model;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object wrapping a room's canonical display name — a single, already-normalized string token.
 *
 * <p>The name is derived in ONE direction only, from the room coordinates ({@code RoomLocation + code});
 * it is NEVER reverse-parsed into coordinates to drive any write or constraint logic. The previous
 * regex that split a raw string back into {@code floor}/{@code code} has been removed entirely. A name
 * built from a raw client string ({@link #ofRaw(String)}) is kept opaque (display-only) and is matched
 * exactly against the database; only the coordinate factory populates the forward-known components.</p>
 *
 * <p>Display format: {@code building + "." + String.format("%02d", floor) + code}. The floor is
 * zero-padded to a minimum of 2 digits, the code is a flexible 1&ndash;10 character alphanumeric block
 * (case-insensitive, normalized to upper case). Examples: building "F", floor 5, code "LAB" &rarr;
 * {@code F.05LAB}; floor 12, code "05" &rarr; {@code F.1205}; floor 105, code "205" &rarr; {@code F.105205}.</p>
 */
public final class RoomName {

    private static final Pattern DISPLAY_FORMAT =
            Pattern.compile("^[A-Za-z0-9]+\\.\\d{2,}[A-Za-z0-9]{1,10}$");

    private final String value;
    // Forward-known components, populated ONLY by the coordinate factory (never reverse-parsed).
    private final String building;
    private final int floor;
    private final String code;

    private RoomName(String value, String building, int floor, String code) {
        this.value = value;
        this.building = building;
        this.floor = floor;
        this.code = code;
    }

    /**
     * Composes a name from a (normalized) location and a code — the system-generated, downward path.
     * Equivalent to {@code fromCoordinate}; kept as {@code of} for idiom with the rest of the codebase.
     */
    public static @NonNull RoomName of(@NonNull RoomLocation location, String code) {
        String normalizedCode = normalizeCode(code);
        String value = location.building() + "." + String.format("%02d", location.floor()) + normalizedCode;
        return new RoomName(value, location.building(), location.floor(), normalizedCode);
    }

    /**
     * Wraps a raw name string coming up from a client query — the upward path. This is a pure display
     * token: it is format-gated (RAM self-defense) but NEVER reverse-parsed into coordinates.
     */
    public static @NonNull RoomName ofRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RoomDomainException("Room name must not be blank.");
        }
        String normalized = raw.trim().toUpperCase();
        if (!DISPLAY_FORMAT.matcher(normalized).matches()) {
            throw new RoomDomainException(
                    "Room name must follow the format [Building].[Floor][code], e.g. F.0201.");
        }
        return new RoomName(normalized, null, 0, null);
    }

    private static @NonNull String normalizeCode(String code) {
        if (code == null || !code.trim().matches("^[A-Za-z0-9]{1,10}$")) {
            throw new RoomDomainException("Room code must be 1–10 alphanumeric characters.");
        }
        return code.trim().toUpperCase();
    }

    public String building() {
        return building;
    }

    public int floor() {
        return floor;
    }

    public String code() {
        return code;
    }

    public @NonNull String asString() {
        return value;
    }

    /**
     * True when this name was built from coordinates and its building/floor align with the given
     * location. Raw (client-supplied) names are opaque and never match.
     */
    public boolean matches(RoomLocation location) {
        return location != null && building != null
                && floor == location.floor()
                && building.equals(location.building());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof RoomName that && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
