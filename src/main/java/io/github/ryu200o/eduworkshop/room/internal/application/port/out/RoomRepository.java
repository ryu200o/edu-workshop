package io.github.ryu200o.eduworkshop.room.internal.application.port.out;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;

import java.util.Optional;

/**
 * Outbound port (SPI) for persisting and loading Room aggregates on the write side.
 * Global-uniqueness checks (existsByCoordinate / existsByName) live here as Application-level
 * queries, used by handlers for the check-then-execute pattern per ADR 0005 (Revised).
 */
public interface RoomRepository {

    /**
     * Persists the mutated Room aggregate (write side).
     */
    Room save(Room room);

    /**
     * Loads the persisted Room aggregate by id for write-side mutation. Returns empty when absent.
     */
    Optional<Room> loadById(RoomId id);

    /**
     * {@code true} when another room already occupies the {@code (location, code)} coordinate.
     * Used by handlers for the fast-fail uniqueness check before calling the aggregate.
     */
    boolean existsByCoordinate(RoomLocation location, RoomCode code);

    /**
     * {@code true} when another room already has the {@code (location, name)} pair.
     * Used by handlers for the fast-fail uniqueness check before calling the aggregate.
     */
    boolean existsByName(RoomLocation location, RoomName name);
}
