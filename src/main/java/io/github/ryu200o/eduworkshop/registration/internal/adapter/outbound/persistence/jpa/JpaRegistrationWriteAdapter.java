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
            repository.saveAndFlush(toEntity(registration));
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
        try {
            repository.saveAll(registrations.stream()
                    .map(JpaRegistrationWriteAdapter::toEntity)
                    .toList());
        } catch (DataIntegrityViolationException ex) {
            // Race-proof gate (rào lần 2): the DB unique index is the authoritative guard against
            // concurrent duplicate registrations. The handler's read is only fail-fast UX (rào lần 1).
            throw new DuplicateRegistrationException(
                    registrations.getFirst().workshopReference().workshopId(),
                    registrations.getFirst().studentId().value());
        }
    }

    // ====================== MAPPER ======================

    private static RegistrationJpaEntity toEntity(Registration registration) {
        return new RegistrationJpaEntity(
                registration.id().value(),
                registration.workshopReference().workshopId(),
                registration.studentId().value(),
                registration.state().name(),
                registration.workshopReference().startTime(),
                registration.registeredAt(),
                registration.cancelledAt(),
                registration.createdAt(),
                registration.updatedAt()
        );
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
                entity.getUpdatedAt()
        );
    }
}
