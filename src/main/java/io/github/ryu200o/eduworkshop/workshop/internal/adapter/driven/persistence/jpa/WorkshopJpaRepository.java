package io.github.ryu200o.eduworkshop.workshop.internal.adapter.driven.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface WorkshopJpaRepository extends JpaRepository<WorkshopJpaEntity, UUID> {

    List<WorkshopJpaEntity> findByRoomId(UUID roomId);
}
