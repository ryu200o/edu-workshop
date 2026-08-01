package io.github.ryu200o.eduworkshop.registration.internal.application.port.out;

import io.github.ryu200o.eduworkshop.registration.internal.domain.model.Registration;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port (SPI) for persisting and loading Registration aggregates on the write side.
 * The duplicate-seat lookup (loadByWorkshopAndUser) lives here as an Application-level query, used
 * by handlers for the check-then-execute pattern per ADR 0005 (Revised); the DB unique index
 * {@code uk_registrations_workshop_user} remains the race-proof backstop.
 */
public interface RegistrationRepository {

    /**
     * Persists the mutated Registration aggregate (write side).
     */
    Registration save(Registration registration);

    /**
     * Loads the persisted Registration aggregate by id for write-side mutation. Empty when absent.
     */
    Optional<Registration> loadById(RegistrationId id);

    /**
     * Loads the single registration row for a (workshop, user) pair — the "1-row-per-pair" model
     * (ADR 0012). Returns empty when no row exists yet; the loaded row may be {@code CANCELLED}
     * (re-activation path) or {@code REGISTERED} (duplicate path).
     */
    Optional<Registration> loadByWorkshopAndUser(UUID workshopId, UUID userId);
}
