package io.github.ryu200o.eduworkshop.room.internal.adapter.outbound.persistence.jpa;

import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.MaintenanceScheduleRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.MaintenanceId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.MaintenanceSchedule;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA-backed outbound adapter implementing the {@link MaintenanceScheduleRepository} port. Handles
 * persistence, load and mapping between domain {@link MaintenanceSchedule} and
 * {@link MaintenanceScheduleJpaEntity}. Package-private; hidden inside the module's {@code internal}
 * boundary.
 */
@Component
class JpaMaintenanceScheduleWriteAdapter implements MaintenanceScheduleRepository {

    private final MaintenanceScheduleJpaRepository repository;

    JpaMaintenanceScheduleWriteAdapter(MaintenanceScheduleJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public MaintenanceSchedule save(MaintenanceSchedule schedule) {
        repository.saveAndFlush(toEntity(schedule));
        return schedule;
    }

    @Override
    public List<MaintenanceSchedule> findByRoomId(UUID roomId) {
        return repository.findByRoomId(roomId).stream()
                .map(JpaMaintenanceScheduleWriteAdapter::toSchedule)
                .toList();
    }

    @Override
    public List<MaintenanceSchedule> findOverlapping(UUID roomId, Instant startTime, Instant endTime) {
        return repository.findOverlapping(roomId, startTime, endTime).stream()
                .map(JpaMaintenanceScheduleWriteAdapter::toSchedule)
                .toList();
    }

    @Override
    public void deleteById(MaintenanceId id) {
        repository.deleteById(id.value());
    }

    // ====================== MAPPER ======================

    private static MaintenanceScheduleJpaEntity toEntity(MaintenanceSchedule schedule) {
        return new MaintenanceScheduleJpaEntity(
                schedule.id().value(),
                schedule.roomId().value(),
                schedule.startTime(),
                schedule.endTime(),
                schedule.reason(),
                schedule.createdBy(),
                schedule.createdAt(),
                schedule.updatedAt()
        );
    }

    private static MaintenanceSchedule toSchedule(MaintenanceScheduleJpaEntity entity) {
        return MaintenanceSchedule.reconstruct(
                MaintenanceId.of(entity.getId()),
                RoomId.of(entity.getRoomId()),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getReason(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
