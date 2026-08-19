package io.github.ryu200o.eduworkshop.registration.internal.application.handler;

import io.github.ryu200o.eduworkshop.registration.internal.application.exception.RegistrationNotFoundException;
import io.github.ryu200o.eduworkshop.registration.internal.application.exception.RegistrationRoleViolationException;
import io.github.ryu200o.eduworkshop.registration.internal.application.exception.WorkshopNotVerifiableException;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.command.VerifyRegistrationCommand;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationDomainEventPublisher;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationRepository;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.Registration;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.WorkshopExposeAPI;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopSchedulingContract;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopStateContract;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Orchestrates the "verify a ticket at the door" use case (Epic 3C).
 *
 * <p>Application-layer flow (ADR 0005/0010): authorize the verifier role (only {@code VERIFIER},
 * OQ-3C-1) → load the registration → check the Workshop state gate via
 * {@link WorkshopExposeAPI#getScheduling} (only {@code PUBLISHED} | {@code IN_PROGRESS} may be
 * verified, OQ-3C-2) → delegate the state transition to the aggregate
 * ({@link Registration#verify} — idempotent no-op for an already-verified seat, OQ-3C-3; rejects
 * {@code CANCELLED}/{@code REFUNDED} with 409, OQ-3C-4) → persist → publish events through the
 * outbox.</p>
 */
@Component
class VerifyRegistrationCommandHandler
        implements CommandHandler<VerifyRegistrationCommand> {

    private static final String VERIFIER_ROLE = "VERIFIER";

    private final WorkshopExposeAPI workshopExposeApi;
    private final RegistrationRepository registrationRepository;
    private final RegistrationDomainEventPublisher registrationDomainEventPublisher;
    private final Clock clock;

    VerifyRegistrationCommandHandler(WorkshopExposeAPI workshopExposeApi,
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
    public void handle(VerifyRegistrationCommand command) {
        Instant now = Instant.now(clock);

        if (!VERIFIER_ROLE.equals(command.role())) {
            throw new RegistrationRoleViolationException(command.role(), "verify a ticket");
        }

        Registration registration = registrationRepository.loadById(RegistrationId.of(command.registrationId()))
                .orElseThrow(() -> new RegistrationNotFoundException("id", command.registrationId()));

        WorkshopSchedulingContract workshop = workshopExposeApi.getScheduling(registration.workshopReference().workshopId())
                .orElseThrow(() -> new RegistrationNotFoundException("workshop",
                        registration.workshopReference().workshopId()));

        if (workshop.state() != WorkshopStateContract.PUBLISHED
                && workshop.state() != WorkshopStateContract.IN_PROGRESS) {
            throw new WorkshopNotVerifiableException(registration.workshopReference().workshopId(), workshop.state());
        }

        registration.verify(now);

        registrationRepository.save(registration);

        registrationDomainEventPublisher.publish(registration.recordedEvents());
        registration.clearDomainEvents();
    }
}
