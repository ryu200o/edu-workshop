package io.github.ryu200o.eduworkshop.room.internal.application.port.out;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;

import java.util.Optional;

/**
 * Outbound port (SPI) for persisting and loading Room aggregates on the write side. Consolidates the
 * former {@code RoomStateGateway} (load/save) and the persistence concern of {@code RoomRepository}.
 * Implemented by a driven adapter.
 *
 * <p>The global-uniqueness invariant no longer lives on this port: it is owned by the domain through the
 * {@code RoomUniquenessPolicy} interface, whose IO-backed implementation lives in the infrastructure
 * adapter. This port is therefore a pure persistence contract (save / load), keeping the repository free
 * of set-based invariant concerns.</p>
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
}
