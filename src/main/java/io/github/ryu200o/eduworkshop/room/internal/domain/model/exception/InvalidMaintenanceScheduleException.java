package io.github.ryu200o.eduworkshop.room.internal.domain.model.exception;

/**
 * Raised when a maintenance schedule violates local invariants — for example, a reason shorter
 * than 10 characters, or an {@code endTime} that is not after {@code startTime}.
 */
public final class InvalidMaintenanceScheduleException extends RoomDomainException {

    public InvalidMaintenanceScheduleException(String message) {
        super(message);
    }
}
