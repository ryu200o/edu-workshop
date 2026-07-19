package io.github.ryu200o.eduworkshop.room.internal.application.port.out;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;

import java.util.Optional;

/**
 * Outbound port (SPI) for persisting and guarding Room aggregates on the write side. Consolidates the
 * former {@code RoomStateGateway} (load/save) and {@code RoomExistencePort} (global uniqueness) into a
 * single, business-focused contract. Implemented by a driven adapter.
 */
public interface RoomRepository {

    /**
     * Persists the mutated Room aggregate (write side).
     */
    Room save(Room room);

    /**
     * Loads the persisted Room aggregate by id for write-side mutation. Returns empty when absent, so the
     * handler (or {@code RoomCommandGuard}) can translate a missing room into a {@code RoomNotFoundException}.
     */
    Optional<Room> loadById(RoomId id);

    /**
     * Global-uniqueness gate on the hard business coordinates (building + floor + code). Returns true if ANY
     * room already occupies that coordinate. The caller must exclude the room's own current coordinate
     * (idempotency) to avoid a false-positive self-collision. The DB unique constraint remains the
     * authoritative, race-proof gate (anti-TOCTOU); this read is fail-fast/UX only.
     */
    boolean existsByCoordinate(String building, int floor, int code);
}
