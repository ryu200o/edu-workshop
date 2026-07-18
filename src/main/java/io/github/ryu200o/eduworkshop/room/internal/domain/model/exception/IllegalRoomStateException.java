package io.github.ryu200o.eduworkshop.room.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomState;

/**
 * Raised when a requested physical-state transition is rejected by the room's invariant.
 *
 * <p>Example: attempting to place a {@link RoomState#DEACTIVATED} room under maintenance,
 * or trying to reactivate a permanently deactivated room.</p>
 */
public class IllegalRoomStateException extends RoomDomainException {

    private final RoomId roomId;
    private final RoomState currentState;
    private final RoomState attemptedState;

    public IllegalRoomStateException(RoomId roomId,
                                     RoomState currentState,
                                     RoomState attemptedState,
                                     String message) {
        super(message);
        this.roomId = roomId;
        this.currentState = currentState;
        this.attemptedState = attemptedState;
    }

    public RoomId getRoomId() {
        return roomId;
    }

    public RoomState getCurrentState() {
        return currentState;
    }

    public RoomState getAttemptedState() {
        return attemptedState;
    }
}
