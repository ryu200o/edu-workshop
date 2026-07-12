package io.github.ryu200o.eduworkshop.room.internal.application.port.out;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomName;

/**
 * Outbound port (SPI) for the global uniqueness invariant: checks whether a room with the given
 * name at the given location already exists system-wide. Implemented by a driven adapter.
 */
public interface RoomExistencePort {

    boolean existsByNameAndLocation(RoomName name, RoomLocation location);
}
