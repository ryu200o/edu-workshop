package io.github.ryu200o.eduworkshop.room.internal.adapter.outbound.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for {@link MaintenanceScheduleJpaEntity}. Package-private — reachable only
 * from within the outbound persistence adapter package.
 */
interface MaintenanceScheduleJpaRepository extends JpaRepository<MaintenanceScheduleJpaEntity, UUID> {

    /**
     * Returns all maintenance schedules for a given room.
     */
    List<MaintenanceScheduleJpaEntity> findByRoomId(UUID roomId);

    /**
     * Finds maintenance schedules that overlap a given time window.
     *
     * Overlap condition: {@code existingStart < newEnd && existingEnd > newStart}.
     * When {@code endTime} is null (indefinite), all schedules starting before the new start
     * match (existingEnd > newStart is always true when existingEnd is null).
     */
    @Query("""
            SELECT m FROM MaintenanceScheduleJpaEntity m
            WHERE m.roomId = :roomId
              AND (:endTime IS NULL OR m.startTime < :endTime)
              AND (m.endTime IS NULL OR m.endTime > :startTime)
            """)
    List<MaintenanceScheduleJpaEntity> findOverlapping(
            @Param("roomId") UUID roomId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    /**
     * Returns whether any maintenance schedule overlaps a given time window, using a SQL {@code EXISTS}
     * (derived from {@link #findOverlapping} collapsed to a boolean) so the DB short-circuits on the
     * first matching row and no aggregates are materialized on the JVM.
     *
     * Overlap condition: {@code existingStart < newEnd && existingEnd > newStart}.
     * When {@code endTime} is null (indefinite), all schedules starting before the new start match.
     */
    @Query("""
            SELECT COUNT(m) > 0 FROM MaintenanceScheduleJpaEntity m
            WHERE m.roomId = :roomId
              AND (:endTime IS NULL OR m.startTime < :endTime)
              AND (m.endTime IS NULL OR m.endTime > :startTime)
            """)
    boolean existsOverlapping(
            @Param("roomId") UUID roomId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);
}
