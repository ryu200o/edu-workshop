package io.github.ryu200o.eduworkshop.room.internal.adapter.driven.persistence;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.view.RoomDetailView;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.view.RoomSummaryView;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomExistencePort;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomQueryPort;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomStateGateway;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.entity.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.state.RoomState;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomName;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed driven adapter implementing all Room outbound ports (write, existence, read). Replaces
 * the earlier in-memory adapter. Domain &harr; entity mapping and domain &rarr; {@code RoomDetailView}
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
    public boolean existsByBuildingAndFloorAndCode(String building, int floor, String code) {
        return repository.existsByBuildingAndFloorAndCode(building, floor, code);
    }

    @Override
    public Optional<Room> loadById(UUID id) {
        return repository.findById(id).map(JpaRoomAdapter::toRoom);
    }

    @Override
    public Room save(Room room) {
        repository.save(toEntity(room));
        return room;
    }

    // ── Read port (side-effect free) ─────────────────────────────────────────
    @Override
    public Optional<RoomDetailView> findById(UUID id) {
        return repository.findById(id).map(JpaRoomAdapter::toDetailView);
    }

    @Override
    public Optional<RoomSummaryView> findByName(@NonNull RoomName name) {
        return repository.findByName(name.asString()).map(JpaRoomAdapter::toSummaryView);
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
    private static @NonNull Room toRoom(@NonNull RoomJpaEntity entity) {
        RoomLocation location = RoomLocation.reconstruct(entity.getBuilding(), entity.getFloor());
        RoomName name = RoomName.of(location, entity.getCode());
        RoomState state = RoomState.valueOf(entity.getState());
        return Room.reconstruct(entity.getId(), name, location, entity.getCapacity(), state,
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    @Contract("_ -> new")
    private static @NonNull RoomDetailView toDetailView(@NonNull RoomJpaEntity entity) {
        return new RoomDetailView(
                entity.getId(),
                entity.getName(),
                entity.getBuilding(),
                entity.getFloor(),
                entity.getCapacity(),
                entity.getState()
        );
    }

    @Contract("_ -> new")
    private static @NonNull RoomSummaryView toSummaryView(@NonNull RoomJpaEntity entity) {
        return new RoomSummaryView(
                entity.getId(),
                entity.getName(),
                entity.getBuilding(),
                entity.getFloor()
        );
    }
}
