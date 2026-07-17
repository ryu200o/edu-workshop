package io.github.ryu200o.eduworkshop.room.internal.adapter.driven.persistence.jpa;

import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomState;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed driven adapter implementing the Room write port ({@link RoomRepository}). Handles aggregate
 * mutation, load and the global-uniqueness gate on the hard business coordinates. Domain &harr; entity
 * mapping is performed entirely here, keeping the domain framework-free. Package-private; hidden inside
 * the module's {@code internal} boundary.
 */
@Component
class JpaRoomWriteAdapter implements RoomRepository {

    private final RoomJpaRepository repository;

    JpaRoomWriteAdapter(RoomJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByCoordinate(String building, int floor, String code) {
        return repository.existsByBuildingAndFloorAndCode(building, floor, code);
    }

    @Override
    public Optional<Room> loadById(UUID id) {
        return repository.findById(id).map(JpaRoomWriteAdapter::toRoom);
    }

    @Override
    public Room save(Room room) {
        repository.save(toEntity(room));
        return room;
    }

    private static RoomJpaEntity toEntity(Room room) {
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

    private static Room toRoom(RoomJpaEntity entity) {
        RoomLocation location = RoomLocation.reconstruct(entity.getBuilding(), entity.getFloor());
        RoomName name = RoomName.of(location, entity.getCode());
        RoomState state = RoomState.valueOf(entity.getState());
        return Room.reconstruct(entity.getId(), name, location, entity.getCapacity(), state,
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
