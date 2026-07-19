package io.github.ryu200o.eduworkshop.room.internal.domain.model;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;

import java.util.Objects;

/**
 * Value Object wrapping a room's display name — a single, opaque string token.
 *
 * <p>The name is now free-form: the business owns the naming convention and accepts the risk of
 * duplicates/format. This VO only enforces the non-blank invariant (RAM self-defense) and normalizes
 * the string (trim + upper-case). All coordinate-derived logic (building/floor/code parsing) has been
 * removed — {@code name} is fully decoupled from {@link RoomLocation} and the room's {@code code}.
 * The record is retained (rather than collapsing to a bare String) so future name rules can be added
 * back here without touching callers.</p>
 */
public final class RoomName {

    private final String value;

    private RoomName(String value) {
        this.value = value;
    }

    /**
     * Wraps a raw name string coming up from a client. Blank check only — the name is otherwise free-form.
     */
    public static RoomName of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RoomDomainException("Room name must not be blank.");
        }
        return new RoomName(raw.trim().toUpperCase());
    }

    public String asString() {
        return value;
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
