package io.github.ryu200o.eduworkshop.room.internal.domain.model.value;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Value Object enforcing the room naming rule: {@code [Building].[Floor][2-digit code]}.
 *
 * <p>Examples: building "F", floor 2, code "01" &rarr; {@code F.201}; code "15" &rarr; {@code F.215}.
 * The object self-normalizes and self-defends: any non-conforming input is rejected at construction,
 * so a {@code RoomName} instance is guaranteed to be valid everywhere it is used.</p>
 */
public final class RoomName {

    private static final Pattern FORMAT = Pattern.compile("^([A-Za-z0-9]+)\\.(\\d+)(\\d{2})$");

    private final String building;
    private final int floor;
    private final String code;

    private RoomName(String building, int floor, String code) {
        this.building = building;
        this.floor = floor;
        this.code = code;
    }

    /**
     * Composes a name from a (normalized) location and a 2-digit code — the system-generated path.
     */
    public static @NonNull RoomName of(@NonNull RoomLocation location, String code) {
        String normalizedCode = normalizeCode(code);
        return new RoomName(location.building(), location.floor(), normalizedCode);
    }

    /**
     * Parses and validates a raw name string — the self-defense path against external input.
     */
    @Contract("null -> fail")
    public static @NonNull RoomName of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RoomDomainException("Room name must not be blank.");
        }
        Matcher matcher = FORMAT.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new RoomDomainException(
                    "Room name must follow the format [Building].[Floor][2-digit code], e.g. F.201.");
        }
        String building = matcher.group(1).toUpperCase();
        int floor = Integer.parseInt(matcher.group(2));
        String code = matcher.group(3);
        if (floor <= 0) {
            throw new RoomDomainException("Room floor in the name must be a positive integer.");
        }
        return new RoomName(building, floor, code);
    }

    @Contract("null -> fail")
    private static @NonNull String normalizeCode(String code) {
        if (code == null || !code.trim().matches("\\d{2}")) {
            throw new RoomDomainException("Room code must be exactly 2 digits.");
        }
        return code.trim();
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

    @Contract(pure = true)
    public @NonNull String asString() {
        return building + "." + floor + code;
    }

    /**
     * True when this name's building and floor are consistent with the given location.
     */
    public boolean matches(RoomLocation location) {
        return location != null
                && floor == location.floor()
                && building.equals(location.building());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof RoomName that
                && floor == that.floor
                && building.equals(that.building)
                && code.equals(that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(building, floor, code);
    }

    @Override
    public String toString() {
        return asString();
    }
}
