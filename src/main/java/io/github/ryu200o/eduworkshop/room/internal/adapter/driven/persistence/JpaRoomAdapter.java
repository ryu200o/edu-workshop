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

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed driven adapter implementing all Room outbound ports (write, existence, read). Replaces
 * the earlier in-memory adapter. Domain &harr; entity mapping and domain &rarr; {@link RoomResponse}
 * projection are performed entirely here, keeping the domain framework-free (CQRS bypass on reads).
 * Package-private; hidden inside the module's {@code internal} boundary.
 */
@Component
class JpaRoomAdapter implements RoomExistencePort, RoomStateGateway, RoomQueryPort {

    private final RoomJpaRepository repository;

    JpaRoomAdapter(RoomJpaRepository repository) {
        this.repository = repository;
    }

    // ── Write ports ──────────────────────────────────────────────────────────
    @Override
    public boolean existsByNameAndLocation(@NonNull RoomName name, @NonNull RoomLocation location) {
        return repository.existsByBuildingAndFloorAndCode(
                location.building(), location.floor(), name.code());
    }

    @Override
    public Room save(Room room) {
        repository.save(toEntity(room));
        return room;
    }

    // ── Read port (side-effect free) ─────────────────────────────────────────
    @Override
    public Optional<RoomResponse> findById(UUID id) {
        return repository.findById(id).map(JpaRoomAdapter::toResponse);
    }

    @Override
    public Optional<RoomResponse> findByName(@NonNull RoomName name) {
        return repository.findByName(name.asString()).map(JpaRoomAdapter::toResponse);
    }

    // ── Mapping (infrastructure only) ────────────────────────────────────────
    @Contract("_ -> new")
    private static @NonNull RoomJpaEntity toEntity(@NonNull Room room) {
        return new RoomJpaEntity(
                room.id(),
                room.name().asString(),
                room.location().building(),
                room.location().floor(),
                room.name().code(),
                room.capacity(),
                room.state().name(),
                room.createdAt(),
                room.updatedAt()
        );
    }

    @Contract("_ -> new")
    private static @NonNull RoomResponse toResponse(@NonNull RoomJpaEntity entity) {
        return new RoomResponse(
                entity.getId(),
                entity.getName(),
                entity.getBuilding(),
                entity.getFloor(),
                entity.getCapacity(),
                entity.getState()
        );
    }
}
