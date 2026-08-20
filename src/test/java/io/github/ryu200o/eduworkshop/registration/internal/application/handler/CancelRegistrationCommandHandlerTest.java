package io.github.ryu200o.eduworkshop.registration.internal.application.handler;

import io.github.ryu200o.eduworkshop.registration.internal.application.exception.RegistrationNotFoundException;
import io.github.ryu200o.eduworkshop.registration.internal.application.exception.RegistrationNotOwnedByUserException;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.command.CancelRegistrationCommand;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationDomainEventPublisher;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationRepository;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.Registration;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationState;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.StudentId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.WorkshopReference;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.event.RegistrationCancelled;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.exception.CancellationDeadlineExceededException;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.exception.RegistrationDomainException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelRegistrationCommandHandlerTest {

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private RegistrationDomainEventPublisher registrationDomainEventPublisher;

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");
    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private CancelRegistrationCommandHandler handler() {
        return new CancelRegistrationCommandHandler(registrationRepository, registrationDomainEventPublisher, clock);
    }

    private Registration registeredForOwner() {
        return Registration.create(RegistrationId.generate(), StudentId.of(OWNER_ID),
                WorkshopReference.of(WORKSHOP_ID, START), NOW);
    }

    @Test
    void happyPath_cancelsAndPersists() {
        Registration registration = registeredForOwner();
        RegistrationId id = registration.id();
        when(registrationRepository.loadById(id)).thenReturn(Optional.of(registration));
        when(registrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(new CancelRegistrationCommand(id.value(), OWNER_ID));

        ArgumentCaptor<Registration> savedCaptor = ArgumentCaptor.forClass(Registration.class);
        verify(registrationRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().id()).isEqualTo(id);
        assertThat(savedCaptor.getValue().state()).isEqualTo(RegistrationState.CANCELLED);
        assertThat(savedCaptor.getValue().cancelledAt()).isEqualTo(NOW);

        verify(registrationDomainEventPublisher).publish(argThat(events -> events.size() == 2
                && events.get(1) instanceof RegistrationCancelled));
    }

    @Test
    void rejectsWhenRequesterIsNotTheOwner() {
        Registration registration = registeredForOwner();
        when(registrationRepository.loadById(registration.id())).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> handler().handle(new CancelRegistrationCommand(registration.id().value(), UUID.randomUUID())))
                .isInstanceOf(RegistrationNotOwnedByUserException.class);

        verifyNoInteractions(registrationDomainEventPublisher);
    }

    @Test
    void rejectsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(registrationRepository.loadById(RegistrationId.of(id))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new CancelRegistrationCommand(id, OWNER_ID)))
                .isInstanceOf(RegistrationNotFoundException.class);

        verifyNoInteractions(registrationDomainEventPublisher);
    }

    @Test
    void rejectsCancellationAfterDeadline() {
        // START's deadline has passed relative to NOW.
        Instant lateStart = NOW.plus(java.time.Duration.ofHours(10));
        Registration registration = Registration.create(RegistrationId.generate(), StudentId.of(OWNER_ID),
                WorkshopReference.of(WORKSHOP_ID, lateStart), NOW);
        when(registrationRepository.loadById(registration.id())).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> handler().handle(new CancelRegistrationCommand(registration.id().value(), OWNER_ID)))
                .isInstanceOf(CancellationDeadlineExceededException.class);

        verifyNoInteractions(registrationDomainEventPublisher);
    }

    @Test
    void rejectsCancellingAnAlreadyCancelledRegistration() {
        Registration registration = registeredForOwner();
        registration.cancel(START.minus(Registration.CANCELLATION_DEADLINE).minusSeconds(1));
        when(registrationRepository.loadById(registration.id())).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> handler().handle(new CancelRegistrationCommand(registration.id().value(), OWNER_ID)))
                .isInstanceOf(RegistrationDomainException.class);

        verifyNoInteractions(registrationDomainEventPublisher);
    }
}
