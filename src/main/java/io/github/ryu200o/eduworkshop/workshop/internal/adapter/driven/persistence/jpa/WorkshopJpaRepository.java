package io.github.ryu200o.eduworkshop.workshop.internal.adapter.driven.persistence.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface WorkshopJpaRepository extends JpaRepository<WorkshopJpaEntity, UUID> {

    List<WorkshopJpaEntity> findByRoomId(UUID roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WorkshopJpaEntity w WHERE w.id = :id")
    java.util.Optional<WorkshopJpaEntity> findByIdWithLock(@Param("id") UUID id);

    @Query("""
            SELECT COUNT(w) FROM WorkshopJpaEntity w
            WHERE w.roomId = :roomId
              AND w.state = 'PUBLISHED'
              AND w.startTime < :endTime
              AND w.endTime > :startTime
              AND w.id <> :excludeWorkshopId
            """)
    int countOverlapping(@Param("roomId") UUID roomId,
                         @Param("startTime") Instant startTime,
                         @Param("endTime") Instant endTime,
                         @Param("excludeWorkshopId") UUID excludeWorkshopId);
}
