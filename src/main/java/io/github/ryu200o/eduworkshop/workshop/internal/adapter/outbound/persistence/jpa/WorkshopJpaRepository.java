package io.github.ryu200o.eduworkshop.workshop.internal.adapter.outbound.persistence.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface WorkshopJpaRepository extends JpaRepository<WorkshopJpaEntity, UUID> {

    List<WorkshopJpaEntity> findByRoomId(UUID roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WorkshopJpaEntity w WHERE w.id = :id")
    Optional<WorkshopJpaEntity> findByIdWithLock(@Param("id") UUID id);

    /**
     * Set-based pessimistic lock (ADR 0015 / ADR 0008 / ADR 0018): locks every PLANNED or PUBLISHED
     * workshop in the room whose Occupancy Window {@code [occupancy_start, end_time]} overlaps the
     * given Occupancy Window. Native predicate on the denormalized {@code occupancy_start} (approved
     * by the composite index {@code idx_workshops_room_occupancy}); no in-memory filter. Serializes
     * concurrent publish/reschedule/change-room operations in the same room/window (lock-set-first)
     * and closes the write-skew double-booking gap.
     *
     * @param targetStartTime the target Occupancy Window start (inclusive lower bound)
     * @param targetEndTime   the target Occupancy Window end (inclusive upper bound)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT w FROM WorkshopJpaEntity w
            WHERE w.roomId = :roomId
              AND w.state IN ('PUBLISHED', 'PLANNED')
              AND w.endTime > :targetStartTime
              AND w.occupancyStart < :targetEndTime
            """)
    List<WorkshopJpaEntity> findPublishedAndPlannedOverlappingWithLock(@Param("roomId") UUID roomId,
                                                                      @Param("targetStartTime") Instant targetStartTime,
                                                                      @Param("targetEndTime") Instant targetEndTime);

    @Query("""
            SELECT w FROM WorkshopJpaEntity w
            WHERE w.roomId = :roomId
              AND w.state = 'PUBLISHED'
              AND (:targetEndTime IS NULL OR w.occupancyStart < :targetEndTime)
              AND w.endTime > :targetStartTime
            """)
    List<WorkshopJpaEntity> loadPublishedOverlappingWithTimeWindow(@Param("roomId") UUID roomId,
                                                                  @Param("targetStartTime") Instant targetStartTime,
                                                                  @Param("targetEndTime") Instant targetEndTime);
}
