package io.github.ryu200o.eduworkshop.room.internal.application.port.out;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.entity.Room;

/**
 * Outbound port (SPI) for persisting Room aggregates (write side). Implemented by a driven adapter.
 */
public interface RoomStateGateway {

    Room save(Room room);
}
