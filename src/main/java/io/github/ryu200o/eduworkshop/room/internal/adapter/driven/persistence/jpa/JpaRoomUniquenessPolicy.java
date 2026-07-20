package io.github.ryu200o.eduworkshop.room.internal.adapter.driven.persistence.jpa;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.policy.RoomUniquenessPolicy;
import org.springframework.stereotype.Component;

/**
 * Infrastructure implementation of the domain-owned {@link RoomUniquenessPolicy}. It translates the two
 * pure domain uniqueness questions into JPA existence queries against the {@link RoomJpaRepository},
 * decomposing the {@link RoomLocation} value object into the scalar {@code building}/{@code floor} columns
 * the entity persists. This is the only place where the global invariant touches IO — the Domain depends
 * solely on the policy interface and stays IO-free (Dependency Inversion / Hexagonal).
 */
@Component
class JpaRoomUniquenessPolicy implements RoomUniquenessPolicy {

    private final RoomJpaRepository repository;

    JpaRoomUniquenessPolicy(RoomJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isCodeUnique(RoomLocation location, RoomCode code) {
        return !repository.existsByBuildingAndFloorAndCode(location.building(), location.floor(), code.value());
    }

    @Override
    public boolean isNameUnique(RoomLocation location, RoomName name) {
        return !repository.existsByBuildingAndFloorAndName(location.building(), location.floor(), name.asString());
    }
}
