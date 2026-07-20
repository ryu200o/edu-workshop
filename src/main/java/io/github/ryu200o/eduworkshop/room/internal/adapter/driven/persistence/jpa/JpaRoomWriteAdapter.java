package io.github.ryu200o.eduworkshop.room.internal.adapter.driven.persistence.jpa;

import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomState;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JPA-backed driven adapter implementing the Room write port ({@link RoomRepository}). Handles aggregate
 * mutation, load and the global-uniqueness gate on the hard business coordinates. Domain &harr; entity
 * mapping is performed entirely here, keeping the domain framework-free. Persistence exception translation
 * is delegated to {@link JpaRoomPersistenceExceptionTranslator}. Package-private; hidden inside the
 * module's {@code internal} boundary.
 */
@Component
class JpaRoomWriteAdapter implements RoomRepository {

    private final RoomJpaRepository repository;
    private final JpaRoomPersistenceExceptionTranslator exceptionTranslator;

    JpaRoomWriteAdapter(RoomJpaRepository repository,
            JpaRoomPersistenceExceptionTranslator exceptionTranslator) {
        this.repository = repository;
        this.exceptionTranslator = exceptionTranslator;
    }

    @Override
    public Optional<Room> loadById(RoomId id) {
        return repository.findById(id.value()).map(JpaRoomWriteAdapter::toRoom);
    }

    @Override
    public Room save(Room room) {
        try {
            repository.saveAndFlush(toEntity(room));
        } catch (DataIntegrityViolationException ex) {
            // Race-proof gate (rào lần 2): the DB unique constraints are the authoritative guard against
            // concurrent duplicate coordinates/names. The aggregate's policy check is only fail-fast UX
            // (rào lần 1). The violation is translated into domain vocabulary with an accurate type so the
            // caller sees a clean, non-misleading business exception.
            throw exceptionTranslator.translate(ex, room);
        }
        return room;
    }

    // ====================== MAPPER ======================

    private static RoomJpaEntity toEntity(Room room) {
        return new RoomJpaEntity(
                room.id().value(),
                room.name().asString(),
                room.location().building(),
                room.location().floor(),
                room.code(),
                room.capacity(),
                room.state().name(),
                room.createdAt(),
                room.updatedAt()
        );
    }

    private static Room toRoom(RoomJpaEntity entity) {
        RoomLocation location = RoomLocation.reconstruct(entity.getBuilding(), entity.getFloor());
        RoomName name = RoomName.of(entity.getName());
        RoomState state = RoomState.valueOf(entity.getState());
        return Room.reconstruct(RoomId.of(entity.getId()), name, location, entity.getCode(),
                entity.getCapacity(), state, entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
