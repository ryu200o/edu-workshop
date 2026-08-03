package io.github.ryu200o.eduworkshop.room.internal.adapter.outbound.persistence.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link RoomJpaEntity}. Package-private — reachable only from within the
 * outbound persistence adapter package.
 */
interface RoomJpaRepository extends JpaRepository<RoomJpaEntity, UUID> {

    /**
     * Global-uniqueness gate on the hard business coordinates (building + floor + code) — independent
     * of any RAM string-composition of the room name.
     */
    boolean existsByBuildingAndFloorAndCode(String building, int floor, int code);

    boolean existsByBuildingAndFloorAndName(String building, int floor, String name);

    Optional<RoomJpaEntity> findByName(String name);

    /**
     * Loads a room by id under a {@code SELECT ... FOR UPDATE} pessimistic write lock (ADR 0015).
     * Serializes concurrent transactions that mutate the same aggregate root so set-based /
     * temporal-overlap invariants are validated consistently.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoomJpaEntity r where r.id = :id")
    Optional<RoomJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
