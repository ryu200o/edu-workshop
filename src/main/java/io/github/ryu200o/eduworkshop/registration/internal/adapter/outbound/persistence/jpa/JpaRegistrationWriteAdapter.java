package io.github.ryu200o.eduworkshop.registration.internal.adapter.outbound.persistence.jpa;

import io.github.ryu200o.eduworkshop.registration.internal.application.exception.DuplicateRegistrationException;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationRepository;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.Registration;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationState;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.StudentId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.WorkshopReference;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed outbound adapter implementing the Registration write port ({@link RegistrationRepository}).
 * Handles aggregate mutation and load. Domain ↔ entity mapping is performed entirely here, keeping
 * the domain framework-free. The unique index {@code uk_registrations_workshop_user} is the
 * race-proof backstop for the duplicate-seat rule: a {@link DataIntegrityViolationException} from a
 * concurrent insert is translated into the business exception {@link DuplicateRegistrationException}.
 * Package-private; hidden inside the module's {@code internal} boundary.
 */
@Component
class JpaRegistrationWriteAdapter implements RegistrationRepository {

    private final RegistrationJpaRepository repository;

    JpaRegistrationWriteAdapter(RegistrationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Registration save(Registration registration) {
        try {
            // Managed-entity copy pattern (ADR 0015 Strategy B): reuse the persistence-context
            // instance so the @Version column is preserved and checked on flush. saveAndFlush() is
            // kept here (Rule 1) so the DataIntegrityViolationException of the unique-seat backstop
            // (uk_registrations_workshop_user) surfaces inside this try-catch for translation.
            RegistrationJpaEntity entity = repository.findById(registration.id().value())
                    .map(existing -> copyTo(existing, registration))
                    .orElseGet(() -> toEntity(registration));
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            // Race-proof gate (rào lần 2): the DB unique index is the authoritative guard against
            // concurrent duplicate registrations. The handler's read is only fail-fast UX (rào lần 1).
            throw new DuplicateRegistrationException(
                    registration.workshopReference().workshopId(),
                    registration.studentId().value());
        }
        return registration;
    }

    @Override
    public Optional<Registration> loadById(RegistrationId id) {
        return repository.findById(id.value()).map(JpaRegistrationWriteAdapter::toRegistration);
    }

    @Override
    public Optional<Registration> loadByWorkshopAndUser(UUID workshopId, UUID userId) {
        return repository.findByWorkshopIdAndUserId(workshopId, userId)
                .map(JpaRegistrationWriteAdapter::toRegistration);
    }

    @Override
    public List<Registration> loadAllByWorkshopIdAndState(UUID workshopId, RegistrationState state) {
        return repository.findByWorkshopIdAndStatus(workshopId, state.name()).stream()
                .map(JpaRegistrationWriteAdapter::toRegistration)
                .toList();
    }

    @Override
    public void saveAll(List<Registration> registrations) {
        // Plain saveAll (Rule 2): the flush is deferred to commit so Hibernate batches all UPDATEs.
        // This path is used only by event handlers flipping EXISTING rows (no unique-violation
        // scenario), so no try-catch translation is required here.
        List<RegistrationJpaEntity> entities = registrations.stream()
                .map(registration -> repository.findById(registration.id().value())
                        .map(existing -> copyTo(existing, registration))
                        .orElseGet(() -> toEntity(registration)))
                .toList();
        repository.saveAll(entities);
    }

    // ====================== MAPPER ======================

    private static RegistrationJpaEntity toEntity(Registration registration) {
        RegistrationJpaEntity entity = new RegistrationJpaEntity();
        entity.setId(registration.id().value());
        return copyTo(entity, registration);
    }

    /**
     * Copies the mutable business fields of the aggregate onto an existing (managed) entity, leaving
     * {@code id} and {@code version} untouched so Hibernate increments/checks the optimistic-lock
     * version on flush.
     */
    private static RegistrationJpaEntity copyTo(RegistrationJpaEntity entity, Registration registration) {
        entity.setWorkshopId(registration.workshopReference().workshopId());
        entity.setUserId(registration.studentId().value());
        entity.setStatus(registration.state().name());
        entity.setWorkshopStartTime(registration.workshopReference().startTime());
        entity.setRegisteredAt(registration.registeredAt());
        entity.setCancelledAt(registration.cancelledAt());
        entity.setGracePeriodUntil(registration.gracePeriodUntil());
        entity.setCreatedAt(registration.createdAt());
        entity.setUpdatedAt(registration.updatedAt());
        return entity;
    }

    private static Registration toRegistration(RegistrationJpaEntity entity) {
        return Registration.reconstruct(
                RegistrationId.of(entity.getId()),
                StudentId.of(entity.getUserId()),
                WorkshopReference.of(entity.getWorkshopId(), entity.getWorkshopStartTime()),
                RegistrationState.valueOf(entity.getStatus()),
                entity.getRegisteredAt(),
                entity.getCancelledAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getGracePeriodUntil()
        );
    }
}
