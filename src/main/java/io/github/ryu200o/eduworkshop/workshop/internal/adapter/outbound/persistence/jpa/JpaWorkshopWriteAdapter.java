package io.github.ryu200o.eduworkshop.workshop.internal.adapter.outbound.persistence.jpa;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopLateThreshold;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class JpaWorkshopWriteAdapter implements WorkshopRepository {

    private final WorkshopJpaRepository repository;

    JpaWorkshopWriteAdapter(WorkshopJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Workshop save(Workshop workshop) {
        // Managed-entity copy pattern (ADR 0015 Strategy B): reuse the persistence-context instance
        // so the @Version column is preserved and checked on flush. Plain save() (Rule 2): the flush
        // is deferred to commit, enabling JDBC batching; the workshops table has no unique constraint,
        // so no synchronous constraint-translation is needed here.
        WorkshopJpaEntity entity = repository.findById(workshop.id().value())
                .map(existing -> copyTo(existing, workshop))
                .orElseGet(() -> toEntity(workshop));
        repository.save(entity);
        return workshop;
    }

    @Override
    public List<Workshop> saveAll(List<Workshop> workshops) {
        // Per-item findById hits the 1st-level cache (entities loaded earlier in the same TX stay
        // managed), then plain saveAll defers the flush so Hibernate batches all UPDATEs.
        List<WorkshopJpaEntity> entities = workshops.stream()
                .map(workshop -> repository.findById(workshop.id().value())
                        .map(existing -> copyTo(existing, workshop))
                        .orElseGet(() -> toEntity(workshop)))
                .toList();
        repository.saveAll(entities);
        return workshops;
    }

    @Override
    public List<Workshop> loadPublishedAndPlannedOverlappingWithLock(UUID roomId, Instant targetStartTime, Instant targetEndTime) {
        return repository.findPublishedAndPlannedOverlappingWithLock(roomId, targetStartTime, targetEndTime).stream()
                .map(this::toWorkshop)
                .toList();
    }

    @Override
    public List<Workshop> loadPublishedOverlappingWithTimeWindow(UUID roomId, Instant targetStartTime, Instant targetEndTime) {
        return repository.loadPublishedOverlappingWithTimeWindow(roomId, targetStartTime, targetEndTime).stream()
                .map(this::toWorkshop)
                .toList();
    }

    @Override
    public Optional<Workshop> loadById(WorkshopId id) {
        return repository.findById(id.value()).map(this::toWorkshop);
    }

    @Override
    public Optional<Workshop> loadByIdWithLock(WorkshopId id) {
        return repository.findByIdWithLock(id.value()).map(this::toWorkshop);
    }

    @Override
    public List<Workshop> loadByRoomId(UUID roomId) {
        return repository.findByRoomId(roomId).stream()
                .map(this::toWorkshop)
                .toList();
    }

    private WorkshopJpaEntity toEntity(Workshop workshop) {
        WorkshopJpaEntity entity = new WorkshopJpaEntity();
        entity.setId(workshop.id().value());
        return copyTo(entity, workshop);
    }

    /**
     * Copies the mutable business fields of the aggregate onto an existing (managed) entity, leaving
     * {@code id} and {@code version} untouched so Hibernate increments/checks the optimistic-lock
     * version on flush.
     */
    private WorkshopJpaEntity copyTo(WorkshopJpaEntity entity, Workshop workshop) {
        entity.setTitle(workshop.title().value());
        entity.setDescription(workshop.description() != null ? workshop.description().value() : null);
        entity.setRoomId(workshop.roomReference() != null ? workshop.roomReference().roomId() : null);
        entity.setRoomNameSnapshot(workshop.roomReference() != null ? workshop.roomReference().roomNameSnapshot() : null);
        entity.setRoomLocationSnapshot(workshop.roomReference() != null ? workshop.roomReference().roomLocationSnapshot() : null);
        entity.setRoomCapacitySnapshot(workshop.roomReference() != null ? workshop.roomReference().roomCapacitySnapshot() : null);
        entity.setHasRoomWarning(workshop.hasRoomWarning());
        entity.setIsRoomEvicted(workshop.isRoomEvicted());
        entity.setRoomEvictedAt(workshop.roomEvictedAt());
        entity.setStartTime(workshop.startTime());
        entity.setEndTime(workshop.endTime());
        entity.setOccupancyStart(workshop.occupancyStart());
        entity.setCapacity(workshop.capacity().value());
        entity.setLateThresholdSeconds(workshop.lateThreshold().seconds());
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
                        ? RoomReference.of(
                                entity.getRoomId(),
                                entity.getRoomNameSnapshot(),
                                entity.getRoomLocationSnapshot(),
                                entity.getRoomCapacitySnapshot() != null ? entity.getRoomCapacitySnapshot() : 0)
                        : null,
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getOccupancyStart(),
                WorkshopCapacity.of(entity.getCapacity()),
                WorkshopLateThreshold.of(entity.getLateThresholdSeconds()),
                entity.isHasRoomWarning(),
                entity.isRoomEvicted(),
                entity.getRoomEvictedAt(),
                entity.getState(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
