package io.github.ryu200o.eduworkshop.room.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;

/**
 * Business exception raised when a room would collide with an existing one on one of the two global
 * invariants: the {@code (building, floor, code)} coordinate or the {@code (building, floor, name)} pair.
 * The type is domain vocabulary; it is thrown by the application handler after the outbound existence
 * check, or translated from a {@code DataIntegrityViolationException} by the write adapter's race gate.
 * The domain performs no IO itself.
 */
public class DuplicateRoomException extends RoomDomainException {

    private final Reason reason;

    public DuplicateRoomException(RoomName name, RoomLocation location) {
        this(Reason.NAME, 0, name, location);
    }

    public DuplicateRoomException(Reason reason, int code, RoomName name, RoomLocation location) {
        super(switch (reason) {
            case CODE -> "A room with code " + code + " already exists at " + location.asString() + ".";
            case NAME -> "A room named '" + name.asString() + "' already exists at " + location.asString() + ".";
        });
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        CODE,
        NAME
    }
}
