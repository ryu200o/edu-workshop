package io.github.ryu200o.eduworkshop.room.internal.application.port.out;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;

/**
 * Outbound port (SPI) for the global uniqueness invariant: checks whether a room with the given
 * name at the given location already exists system-wide. Implemented by a driven adapter.
 */
public interface RoomExistencePort {

    boolean existsByNameAndLocation(RoomName name, RoomLocation location);

    /**
     * Global-uniqueness gate on the hard business coordinates (building + floor + code) for a *target*
     * rename. Returns true if ANY room already occupies that coordinate. The caller must exclude the
     * room's own current code (idempotency) to avoid a false-positive self-collision.
     */
    boolean existsByBuildingAndFloorAndCode(String building, int floor, String code);
}
