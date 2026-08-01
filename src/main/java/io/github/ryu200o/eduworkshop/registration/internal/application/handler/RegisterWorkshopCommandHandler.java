package io.github.ryu200o.eduworkshop.registration.internal.application.handler;

import io.github.ryu200o.eduworkshop.registration.internal.application.exception.DuplicateRegistrationException;
import io.github.ryu200o.eduworkshop.registration.internal.application.exception.ReferencedWorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.registration.internal.application.exception.WorkshopNotOpenForRegistrationException;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.command.RegisterWorkshopCommand;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationDomainEventPublisher;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationRepository;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.Registration;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationState;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.StudentId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.WorkshopReference;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.WorkshopExposeAPI;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopRegistrationContract;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopStateContract;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Orchestrates the "book a seat" use case.
 *
 * <p>Application-layer flow (ADR 0005): verify the referenced workshop exists and is open for
 * booking (PUBLISHED, via {@link WorkshopExposeAPI}) → fast-fail duplicate check on the (workshop,
 * user) pair → build the {@link WorkshopReference} (logical id + startTime snapshot, ADR 0007) →
 * either create a fresh registration or re-activate a previously cancelled row (ADR 0012) → persist
 * → publish domain events through the outbox.</p>
 *
 * <p>The uniqueness rule is set-based and therefore orchestrated here; the DB unique index
 * {@code uk_registrations_workshop_user} is the race-proof backstop (the write adapter translates a
 * {@code DataIntegrityViolationException} into {@link DuplicateRegistrationException}).</p>
 */
@Component
class RegisterWorkshopCommandHandler
        implements CommandHandler<RegisterWorkshopCommand, RegisterWorkshopCommand.Result> {

    private final WorkshopExposeAPI workshopExposeApi;
    private final RegistrationRepository registrationRepository;
    private final RegistrationDomainEventPublisher registrationDomainEventPublisher;
    private final Clock clock;

    RegisterWorkshopCommandHandler(WorkshopExposeAPI workshopExposeApi,
                                   RegistrationRepository registrationRepository,
                                   RegistrationDomainEventPublisher registrationDomainEventPublisher,
                                   Clock clock) {
        this.workshopExposeApi = workshopExposeApi;
        this.registrationRepository = registrationRepository;
        this.registrationDomainEventPublisher = registrationDomainEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RegisterWorkshopCommand.Result handle(RegisterWorkshopCommand command) {
        Instant now = Instant.now(clock);

        WorkshopRegistrationContract workshop = workshopExposeApi.findForRegistration(command.workshopId())
                .orElseThrow(() -> new ReferencedWorkshopNotFoundException(command.workshopId()));

        if (workshop.state() != WorkshopStateContract.PUBLISHED) {
            throw new WorkshopNotOpenForRegistrationException(command.workshopId(), workshop.state());
        }

        WorkshopReference reference = WorkshopReference.of(workshop.workshopId(), workshop.startTime());
        StudentId studentId = StudentId.of(command.userId());

        var existing = registrationRepository.loadByWorkshopAndUser(command.workshopId(), command.userId());

        Registration registration;
        if (existing.isPresent() && existing.get().state() == RegistrationState.REGISTERED) {
            throw new DuplicateRegistrationException(command.workshopId(), command.userId());
        } else if (existing.isPresent()) {
            registration = existing.get();
            registration.reactivate(reference, now);
        } else {
            registration = Registration.create(RegistrationId.generate(), studentId, reference, now);
        }

        registrationRepository.save(registration);

        registrationDomainEventPublisher.publish(registration.recordedEvents());
        registration.clearDomainEvents();

        return new RegisterWorkshopCommand.Result(registration.id().value(), registration.updatedAt());
    }
}
