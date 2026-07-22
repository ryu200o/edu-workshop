package io.github.ryu200o.eduworkshop.workshop.internal.adapter.driven.persistence.jpa;

import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopPersistenceException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.out.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JPA-backed driven adapter implementing the Workshop write port ({@link WorkshopRepository}). Handles
 * aggregate mutation and load. Domain ↔ entity mapping is performed entirely here, keeping the domain
 * framework-free. Persistence failures are wrapped in {@link WorkshopPersistenceException}.
 * Package-private; hidden inside the module's {@code internal} boundary.
 */
@Component
class JpaWorkshopWriteAdapter implements WorkshopRepository {

    private final WorkshopJpaRepository repository;

    JpaWorkshopWriteAdapter(WorkshopJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Workshop save(Workshop workshop) {
        try {
            repository.saveAndFlush(toEntity(workshop));
            return workshop;
        } catch (DataIntegrityViolationException ex) {
            throw new WorkshopPersistenceException(
                    "Failed to persist workshop.",
                    ex
            );
        }
    }

    @Override
    public Optional<Workshop> loadById(WorkshopId id) {
        return repository.findById(id.value()).map(this::toWorkshop);
    }

    private WorkshopJpaEntity toEntity(Workshop workshop) {
        WorkshopJpaEntity entity = new WorkshopJpaEntity();
        entity.setId(workshop.id().value());
        entity.setTitle(workshop.title().value());
        entity.setDescription(workshop.description() != null ? workshop.description().value() : null);
        entity.setRoomId(workshop.roomReference() != null ? workshop.roomReference().roomId() : null);
        entity.setRoomNameSnapshot(workshop.roomReference() != null ? workshop.roomReference().roomNameSnapshot() : null);
        entity.setRoomLocationSnapshot(workshop.roomReference() != null ? workshop.roomReference().roomLocationSnapshot() : null);
        entity.setStartTime(workshop.startTime());
        entity.setEndTime(workshop.endTime());
        entity.setCapacity(workshop.capacity().value());
        entity.setState(workshop.state());
        entity.setCreatedAt(workshop.createdAt());
        entity.setUpdatedAt(workshop.updatedAt());
        return entity;
    }

    private Workshop toWorkshop(WorkshopJpaEntity entity) {
        return Workshop.reconstruct(
                WorkshopId.of(entity.getId()),
                WorkshopTitle.of(entity.getTitle()),
                WorkshopDescription.of(entity.getDescription()),
                entity.getRoomId() != null
                        ? RoomReference.of(entity.getRoomId(), entity.getRoomNameSnapshot(), entity.getRoomLocationSnapshot())
                        : null,
                entity.getStartTime(),
                entity.getEndTime(),
                WorkshopCapacity.of(entity.getCapacity()),
                entity.getState(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
