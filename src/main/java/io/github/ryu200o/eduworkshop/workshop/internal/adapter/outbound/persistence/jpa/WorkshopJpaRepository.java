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
     * Set-based pessimistic lock (ADR 0015 / ADR 0008): locks every PLANNED or PUBLISHED workshop
     * in the room whose window overlaps the given window. Serializes concurrent publish/reschedule/
     * change-room operations in the same room/window (lock-set-first) and closes the write-skew
     * double-booking gap.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT w FROM WorkshopJpaEntity w
            WHERE w.roomId = :roomId
              AND w.state IN ('PUBLISHED', 'PLANNED')
              AND w.startTime < :endTime
              AND w.endTime > :startTime
            """)
    List<WorkshopJpaEntity> findPublishedAndPlannedOverlappingWithLock(@Param("roomId") UUID roomId,
                                                                      @Param("startTime") Instant startTime,
                                                                      @Param("endTime") Instant endTime);

    @Query("""
            SELECT w FROM WorkshopJpaEntity w
            WHERE w.roomId = :roomId
              AND w.state = 'PUBLISHED'
              AND (w.startTime < :endTime OR :endTime IS NULL)
              AND w.endTime > :startTime
            """)
    List<WorkshopJpaEntity> loadPublishedOverlappingWithTimeWindow(@Param("roomId") UUID roomId,
                                                                  @Param("startTime") Instant startTime,
                                                                  @Param("endTime") Instant endTime);
}
