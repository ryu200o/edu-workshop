package io.github.ryu200o.eduworkshop.room.internal.adapter.driven.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link RoomJpaEntity}. Package-private — reachable only from within the
 * driven persistence adapter package.
 */
interface RoomJpaRepository extends JpaRepository<RoomJpaEntity, UUID> {

    /**
     * Global-uniqueness gate on the hard business coordinates (building + floor + code) — independent
     * of any RAM string-composition of the room name.
     */
    boolean existsByBuildingAndFloorAndCode(String building, int floor, String code);

    Optional<RoomJpaEntity> findByName(String name);
}
