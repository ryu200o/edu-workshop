package io.github.ryu200o.eduworkshop.room.internal.application.port.out;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.entity.Room;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port (SPI) for persisting Room aggregates (write side). Implemented by a driven adapter.
 */
public interface RoomStateGateway {

    Room save(Room room);

    /**
     * Loads the persisted Room aggregate by id for write-side mutation. Returns empty when absent,
     * so the handler can translate a missing room into a {@code RoomNotFoundException}.
     */
    Optional<Room> loadById(UUID id);
}
