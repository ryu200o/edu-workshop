package io.github.ryu200o.eduworkshop.room.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.MaintenanceId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.MaintenanceSchedule;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbound port (SPI) for persisting and querying {@link MaintenanceSchedule} entities.
 * The overlap check (global invariant, ADR 0005) is performed via {@link #findOverlapping}.
 */
public interface MaintenanceScheduleRepository {

    /**
     * Persists a new maintenance schedule.
     */
    MaintenanceSchedule save(MaintenanceSchedule schedule);

    /**
     * Loads all maintenance schedules for a given room.
     */
    List<MaintenanceSchedule> findByRoomId(UUID roomId);

    /**
     * Finds maintenance schedules for a given room that overlap the specified time window.
     * Overlap condition: {@code existingStart < newEnd && existingEnd > newStart}.
     * If {@code endTime} is null (indefinite), all schedules starting before {@code endTime} match.
     *
     * @param roomId    the room to check
     * @param startTime the new maintenance window start
     * @param endTime   the new maintenance window end (null = indefinite)
     * @return list of overlapping schedules (empty if none)
     */
    List<MaintenanceSchedule> findOverlapping(UUID roomId, Instant startTime, Instant endTime);

    /**
     * Deletes a maintenance schedule by id.
     */
    void deleteById(MaintenanceId id);
}
