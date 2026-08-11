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
     * Loads every workshop in the given room whose Occupancy Window overlaps the specified Occupancy
     * Window — {@code PLANNED} AND {@code PUBLISHED} states — under a {@code SELECT ... FOR UPDATE}
     * pessimistic write lock (ADR 0015 / ADR 0008 / ADR 0018). Locking the whole overlapping set up
     * front (lock-set-first) closes the write-skew that a single-row target lock allows: two
     * concurrent publishes in the same room/window now serialize on the shared rows instead of both
     * seeing an empty {@code PUBLISHED} set.
     *
     * <p>Overlap is decided on the Occupancy Window (ADR 0018): the native predicate compares the
     * denormalized {@code occupancy_start} (approved by the composite index
     * {@code idx_workshops_room_occupancy (room_id, occupancy_start, end_time)}) — no widened
     * superset, no in-memory filter.</p>
     *
     * <p>The target workshop is deliberately <em>not</em> excluded: callers resolve it from the
     * returned list (it overlaps its own window), so it is covered by the same lock. Used by
     * {@code PublishWorkshopCommandHandler}, {@code RescheduleWorkshopCommandHandler} and
     * {@code ChangeWorkshopRoomCommandHandler} (lock-set-first ordering).</p>
     *
     * @param targetStartTime the target Occupancy Window start (inclusive lower bound)
     * @param targetEndTime   the target Occupancy Window end (inclusive upper bound)
     */
    List<Workshop> loadPublishedAndPlannedOverlappingWithLock(UUID roomId, Instant targetStartTime, Instant targetEndTime);

    /**
     * Loads only the PUBLISHED workshops in the given room whose Occupancy Window overlaps the given
     * maintenance window. Overlap is decided natively on the denormalized {@code occupancy_start};
     * {@code endTime == null} (indefinite maintenance) matches every workshop overlapping from
     * {@code targetStartTime}. Used by {@code WorkshopRoomEventHandler} (Titik 2) to auto-flag
     * affected workshops with an eviction notice without changing their state.
     */
    List<Workshop> loadPublishedOverlappingWithTimeWindow(UUID roomId, Instant targetStartTime, Instant targetEndTime);
}
