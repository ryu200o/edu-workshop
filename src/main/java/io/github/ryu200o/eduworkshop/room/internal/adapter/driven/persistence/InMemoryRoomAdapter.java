package io.github.ryu200o.eduworkshop.room.internal.adapter.driven.persistence;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.RoomResponse;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomExistencePort;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomQueryPort;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomStateGateway;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.entity.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomName;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory driven adapter backing the Room outbound ports with a {@link ConcurrentHashMap}.
 * Serves both the write ports ({@link RoomExistencePort}, {@link RoomStateGateway}) and the read port
 * ({@link RoomQueryPort}). The {@code Room -> RoomResponse} projection mapping is performed here, in
 * the infrastructure layer, so the application read side stays decoupled from the aggregate.
 * Package-private; hidden inside the module's {@code internal} boundary.
 */
@Component
class InMemoryRoomAdapter implements RoomExistencePort, RoomStateGateway, RoomQueryPort {

    private final Map<UUID, Room> store = new ConcurrentHashMap<>();

    // ── Write ports ──────────────────────────────────────────────────────────
    @Override
    public boolean existsByNameAndLocation(RoomName name, RoomLocation location) {
        return store.values().stream()
                .anyMatch(room -> room.name().equals(name) && room.location().equals(location));
    }

    @Override
    public Room save(Room room) {
        store.put(room.id(), room);
        return room;
    }

    // ── Read port (side-effect free) ─────────────────────────────────────────
    @Override
    public Optional<RoomResponse> findById(UUID id) {
        return Optional.ofNullable(store.get(id)).map(InMemoryRoomAdapter::toResponse);
    }

    @Override
    public Optional<RoomResponse> findByName(RoomName name) {
        return store.values().stream()
                .filter(room -> room.name().equals(name))
                .findFirst()
                .map(InMemoryRoomAdapter::toResponse);
    }

    @Contract("_ -> new")
    private static @NonNull RoomResponse toResponse(@NonNull Room room) {
        return new RoomResponse(
                room.id(),
                room.name().asString(),
                room.location().building(),
                room.location().floor(),
                room.capacity(),
                room.state().name()
        );
    }
}
