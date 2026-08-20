package io.github.ryu200o.eduworkshop.registration.internal.application.handler;

import io.github.ryu200o.eduworkshop.registration.internal.application.exception.DuplicateRegistrationException;
import io.github.ryu200o.eduworkshop.registration.internal.application.exception.ReferencedWorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.registration.internal.application.exception.WorkshopCapacityExceededException;
import io.github.ryu200o.eduworkshop.registration.internal.application.exception.WorkshopNotOpenForRegistrationException;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.command.RegisterWorkshopCommand;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationDomainEventPublisher;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationReader;
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
 * <p>Application-layer flow (ADR 0005): acquire the workshop lock-anchor via
 * {@link WorkshopExposeAPI#lockForRegistration} (pessimistic write lock — serializes all concurrent
 * registrations for the same workshop, ADR 0015) → verify the referenced workshop exists and is open
 * for booking (PUBLISHED) → enforce the set-based capacity gate
 * ({@code countActiveByWorkshop < capacity}; exceeded → {@link WorkshopCapacityExceededException}) →
 * fast-fail duplicate check on the (workshop, user) pair → build the {@link WorkshopReference}
 * (logical id + startTime/endTime/title/roomName selective snapshots, ADR 0007) → either create a
 * fresh registration or re-activate a previously cancelled row (ADR 0012) → persist → publish domain
 * events through the outbox.</p>
 *
 * <p>The uniqueness and capacity rules are set-based and therefore orchestrated here; the DB unique
 * index {@code uk_registrations_workshop_user} is the race-proof backstop for duplicates (the write
 * adapter translates a {@code DataIntegrityViolationException} into
 * {@link DuplicateRegistrationException}). Capacity has no DB-side backstop (it lives on the
 * cross-module {@code workshops} row), which is exactly why the workshop row is locked first.</p>
 */
@Component
class RegisterWorkshopCommandHandler
        implements CommandHandler<RegisterWorkshopCommand> {

    private final WorkshopExposeAPI workshopExposeApi;
    private final RegistrationReader registrationReader;
    private final RegistrationRepository registrationRepository;
    private final RegistrationDomainEventPublisher registrationDomainEventPublisher;
    private final Clock clock;

    RegisterWorkshopCommandHandler(WorkshopExposeAPI workshopExposeApi,
                                   RegistrationReader registrationReader,
                                   RegistrationRepository registrationRepository,
                                   RegistrationDomainEventPublisher registrationDomainEventPublisher,
                                   Clock clock) {
        this.workshopExposeApi = workshopExposeApi;
        this.registrationReader = registrationReader;
        this.registrationRepository = registrationRepository;
        this.registrationDomainEventPublisher = registrationDomainEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void handle(RegisterWorkshopCommand command) {
        Instant now = Instant.now(clock);

        WorkshopRegistrationContract workshop = workshopExposeApi.lockForRegistration(command.workshopId())
                .orElseThrow(() -> new ReferencedWorkshopNotFoundException(command.workshopId()));

        if (workshop.state() != WorkshopStateContract.PUBLISHED) {
            throw new WorkshopNotOpenForRegistrationException(command.workshopId(), workshop.state());
        }

        int activeCount = registrationReader.countActiveByWorkshop(command.workshopId());
        if (activeCount >= workshop.capacity()) {
            throw new WorkshopCapacityExceededException(command.workshopId(), workshop.capacity(), activeCount);
        }

        WorkshopReference reference = WorkshopReference.of(workshop.workshopId(), workshop.startTime(),
                workshop.title(), workshop.endTime(), workshop.roomName());
        StudentId studentId = StudentId.of(command.userId());

        var existing = registrationRepository.loadByWorkshopAndUser(command.workshopId(), command.userId());

        Registration registration;
        if (existing.isPresent() && existing.get().state() == RegistrationState.REGISTERED) {
            throw new DuplicateRegistrationException(command.workshopId(), command.userId());
        } else if (existing.isPresent()) {
            registration = existing.get();
            registration.reactivate(reference, now);
        } else {
            registration = Registration.create(RegistrationId.of(command.registrationId()), studentId, reference, now);
        }

        registrationRepository.save(registration);

        registrationDomainEventPublisher.publish(registration.recordedEvents());
        registration.clearDomainEvents();
    }
}
