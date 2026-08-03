package io.github.ryu200o.eduworkshop.room.internal.application.port.outbound;

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
     * Loads the persisted Room aggregate by id, taking a {@code SELECT ... FOR UPDATE} pessimistic
     * write lock (ADR 0015). Blocks concurrent transactions that target the same aggregate root so
     * set-based / temporal-overlap invariants are validated against a consistent snapshot. Returns
     * empty when absent.
     *
     * <p>MUST be used by handlers that validate cross-record invariants (e.g. maintenance-overlap
     * checks) whose correctness depends on more than one persisted record inside the same
     * transaction.</p>
     */
    Optional<Room> loadByIdWithLock(RoomId id);

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
