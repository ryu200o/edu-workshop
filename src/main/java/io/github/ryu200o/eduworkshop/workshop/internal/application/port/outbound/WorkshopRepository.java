package io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port (SPI) for persisting and loading Workshop aggregates on the write side.
 * Implemented by an outbound adapter ({@code JpaWorkshopWriteAdapter}). The save operation
 * wraps database constraint violations into {@code WorkshopPersistenceException}.
 */
public interface WorkshopRepository {

    Workshop save(Workshop workshop);

    List<Workshop> saveAll(List<Workshop> workshops);

    Optional<Workshop> loadById(WorkshopId id);

    Optional<Workshop> loadByIdWithLock(WorkshopId id);

    List<Workshop> loadByRoomId(UUID roomId);

    int countOverlapping(UUID roomId, Instant startTime, Instant endTime, WorkshopId excludeWorkshopId);

    /**
     * Loads only the PLANNED workshops in the given room whose time window overlaps
     * the specified window (excluding the workshop with {@code excludeId}).
     * Pushes the overlap filter into the SQL/JPQL layer so the Application layer
     * never loads irrelevant rows.
     */
    List<Workshop> loadOverlappingPlanned(UUID roomId, Instant startTime, Instant endTime, WorkshopId excludeWorkshopId);

    /**
     * Loads only the PUBLISHED workshops in the given room whose time window overlaps the given
     * maintenance window. Overlap condition: {@code w.startTime < maintEnd && w.endTime > maintStart};
     * when {@code endTime} is null (indefinite maintenance), every workshop starting after
     * {@code startTime} matches. Used by {@code RoomMaintenanceScheduledEventListener} (Titik 2) to
     * auto-flag affected workshops with an eviction notice without changing their state.
     */
    List<Workshop> loadPublishedOverlappingWithTimeWindow(UUID roomId, Instant startTime, Instant endTime);
}
