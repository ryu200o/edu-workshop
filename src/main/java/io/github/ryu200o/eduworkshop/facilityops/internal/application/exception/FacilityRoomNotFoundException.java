package io.github.ryu200o.eduworkshop.facilityops.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ResourceNotFoundException;

import java.util.UUID;

/**
 * Application-layer exception raised by FacilityOps when a requested room cannot be found via
 * {@code RoomExposeAPI}. The FacilityOps module may not import {@code room.internal...} exceptions
 * (information-hiding boundary, ADR 0010), so it owns its own not-found type. This is an application
 * concern, not a domain invariant.
 */
public final class FacilityRoomNotFoundException extends ResourceNotFoundException {

    public FacilityRoomNotFoundException(UUID roomId) {
        super("Room", "id", roomId);
    }
}
