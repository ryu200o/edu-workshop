package io.github.ryu200o.eduworkshop.registration.internal.application.handler;

import io.github.ryu200o.eduworkshop.registration.internal.application.exception.RegistrationNotFoundException;
import io.github.ryu200o.eduworkshop.registration.internal.application.exception.RegistrationNotOwnedByUserException;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.command.CancelRegistrationCommand;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationDomainEventPublisher;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationRepository;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.Registration;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Orchestrates the "cancel a seat" use case.
 *
 * <p>Application-layer flow: load the registration → verify the requester owns the seat (an
 * Application concern — the domain has no notion of "requester") → delegate the deadline check and
 * state transition to the aggregate ({@link Registration#cancel}) → persist → publish events.</p>
 */
@Component
class CancelRegistrationCommandHandler
        implements CommandHandler<CancelRegistrationCommand> {

    private final RegistrationRepository registrationRepository;
    private final RegistrationDomainEventPublisher registrationDomainEventPublisher;
    private final Clock clock;

    CancelRegistrationCommandHandler(RegistrationRepository registrationRepository,
                                     RegistrationDomainEventPublisher registrationDomainEventPublisher,
                                     Clock clock) {
        this.registrationRepository = registrationRepository;
        this.registrationDomainEventPublisher = registrationDomainEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void handle(CancelRegistrationCommand command) {
        Instant now = Instant.now(clock);

        Registration registration = registrationRepository.loadById(RegistrationId.of(command.registrationId()))
                .orElseThrow(() -> new RegistrationNotFoundException("id", command.registrationId()));

        if (!registration.studentId().value().equals(command.userId())) {
            throw new RegistrationNotOwnedByUserException(command.registrationId(), command.userId());
        }

        registration.cancel(now);

        registrationRepository.save(registration);

        registrationDomainEventPublisher.publish(registration.recordedEvents());
        registration.clearDomainEvents();
    }
}
