package io.github.ryu200o.eduworkshop.room.internal.adapter.outbound.persistence.jpa;

import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCapacity;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomState;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JPA-backed outbound adapter implementing the Room write port ({@link RoomRepository}). Handles aggregate
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
    public Optional<Room> loadByIdWithLock(RoomId id) {
        return repository.findByIdForUpdate(id.value()).map(JpaRoomWriteAdapter::toRoom);
    }

    @Override
    public boolean existsByCoordinate(RoomLocation location, RoomCode code) {
        return repository.existsByBuildingAndFloorAndCode(location.building(), location.floor(), code.value());
    }

    @Override
    public boolean existsByName(RoomLocation location, RoomName name) {
        return repository.existsByBuildingAndFloorAndName(location.building(), location.floor(), name.value());
    }

    @Override
    public Room save(Room room) {
        try {
            // Managed-entity copy pattern (ADR 0015 Strategy B): reuse the persistence-context
            // instance so the @Version column is preserved and checked on flush. saveAndFlush() is
            // kept here (Rule 1) so the DataIntegrityViolationException of the unique-coordinate/
            // unique-name backstop surfaces inside this try-catch for translation.
            RoomJpaEntity entity = repository.findById(room.id().value())
                    .map(existing -> copyTo(existing, room))
                    .orElseGet(() -> toEntity(room));
            repository.saveAndFlush(entity);
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
                room.name().value(),
                room.location().building(),
                room.location().floor(),
                room.code().value(),
                room.capacity().value(),
                room.state().name(),
                room.createdAt(),
                room.updatedAt()
        );
    }

    /**
     * Copies the mutable business fields of the aggregate onto an existing (managed) entity, leaving
     * {@code id} and {@code version} untouched so Hibernate increments/checks the optimistic-lock
     * version on flush.
     */
    private static RoomJpaEntity copyTo(RoomJpaEntity entity, Room room) {
        entity.setName(room.name().value());
        entity.setBuilding(room.location().building());
        entity.setFloor(room.location().floor());
        entity.setCode(room.code().value());
        entity.setCapacity(room.capacity().value());
        entity.setState(room.state().name());
        entity.setCreatedAt(room.createdAt());
        entity.setUpdatedAt(room.updatedAt());
        return entity;
    }

    private static Room toRoom(RoomJpaEntity entity) {
        RoomLocation location = RoomLocation.of(entity.getBuilding(), entity.getFloor());
        RoomName name = RoomName.of(entity.getName());
        RoomState state = RoomState.valueOf(entity.getState());
        return Room.reconstruct(RoomId.of(entity.getId()), name, location, RoomCode.of(entity.getCode()),
                RoomCapacity.of(entity.getCapacity()), state, entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
