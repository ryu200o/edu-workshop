package io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port (SPI) for persisting and loading Workshop aggregates on the write side.
 * Implemented by an outbound adapter ({@code JpaWorkshopWriteAdapter}).
 */
public interface WorkshopRepository {

    Workshop save(Workshop workshop);

    List<Workshop> saveAll(List<Workshop> workshops);

    Optional<Workshop> loadById(WorkshopId id);

    Optional<Workshop> loadByIdWithLock(WorkshopId id);

    List<Workshop> loadByRoomId(UUID roomId);

    /**
     * Loads every workshop in the given room whose <em>scheduled occupancy window</em> (Spec v2 / ADR 0018)
     * overlaps the specified window — {@code PLANNED} AND {@code PUBLISHED} states — under a
     * {@code SELECT ... FOR UPDATE} pessimistic write lock (ADR 0015 / ADR 0008). Locking the whole
     * overlapping set up front (lock-set-first) closes the write-skew that a single-row target lock
     * allows: two concurrent publishes in the same room/window now serialize on the shared rows
     * instead of both seeing an empty {@code PUBLISHED} set.
     *
     * <p>The target workshop is deliberately <em>not</em> excluded: callers resolve it from the
     * returned list (it overlaps its own window), so it is covered by the same lock. Used by
     * {@code PublishWorkshopCommandHandler}, {@code RescheduleWorkshopCommandHandler} and
     * {@code ChangeWorkshopRoomCommandHandler} (lock-set-first ordering).</p>
     */
    List<Workshop> loadPublishedAndPlannedOverlappingWithLock(UUID roomId, Instant startTime, Instant endTime);

    /**
     * Loads only the PUBLISHED workshops in the given room whose <em>scheduled occupancy window</em>
     * (Spec v2 / ADR 0018) overlaps the given maintenance window. Overlap condition:
     * {@code w.scheduledOccupancyStart < maintEnd && w.scheduledOccupancyEnd > maintStart};
     * when {@code endTime} is null (indefinite maintenance), every workshop whose occupancy ends after
     * {@code startTime} matches. Used by {@code WorkshopRoomEventHandler} (Titik 2) to
     * auto-flag affected workshops with an eviction notice without changing their state.
     */
    List<Workshop> loadPublishedOverlappingWithTimeWindow(UUID roomId, Instant startTime, Instant endTime);
}
