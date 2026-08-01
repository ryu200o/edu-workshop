package io.github.ryu200o.eduworkshop.registration.internal.adapter.outbound.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link RegistrationJpaEntity}. Package-private — reachable only from
 * within the outbound persistence adapter package.
 */
interface RegistrationJpaRepository extends JpaRepository<RegistrationJpaEntity, UUID> {

    /**
     * Loads the single registration row for a (workshop, user) pair — the "1-row-per-pair" model
     * (ADR 0012). The unique index {@code uk_registrations_workshop_user} guarantees at most one
     * result.
     */
    Optional<RegistrationJpaEntity> findByWorkshopIdAndUserId(UUID workshopId, UUID userId);
}
