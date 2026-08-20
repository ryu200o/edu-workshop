package io.github.ryu200o.eduworkshop.registration.internal.application.handler;

import io.github.ryu200o.eduworkshop.registration.internal.application.exception.RegistrationNotFoundException;
import io.github.ryu200o.eduworkshop.registration.internal.application.exception.RegistrationRoleViolationException;
import io.github.ryu200o.eduworkshop.registration.internal.application.exception.WorkshopNotVerifiableException;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.command.VerifyRegistrationCommand;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationDomainEventPublisher;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationRepository;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.Registration;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationState;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.StudentId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.WorkshopReference;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.event.RegistrationVerified;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.exception.InvalidRegistrationStateException;
import io.github.ryu200o.eduworkshop.workshop.WorkshopExposeAPI;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopSchedulingContract;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopStateContract;

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
class VerifyRegistrationCommandHandlerTest {

    @Mock
    private WorkshopExposeAPI workshopExposeApi;

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private RegistrationDomainEventPublisher registrationDomainEventPublisher;

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");
    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID VERIFIER_ID = UUID.randomUUID();
    private static final String VERIFIER_ROLE = "VERIFIER";

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private VerifyRegistrationCommandHandler handler() {
        return new VerifyRegistrationCommandHandler(
                workshopExposeApi, registrationRepository, registrationDomainEventPublisher, clock);
    }

    private Registration registeredForWorkshop() {
        return Registration.create(RegistrationId.generate(), StudentId.of(UUID.randomUUID()),
                WorkshopReference.of(WORKSHOP_ID, START), NOW);
    }

    private WorkshopSchedulingContract workshop(WorkshopStateContract state) {
        return new WorkshopSchedulingContract(WORKSHOP_ID, state, null);
    }

    private void stubOpenWorkshop() {
        when(workshopExposeApi.getScheduling(WORKSHOP_ID)).thenReturn(Optional.of(workshop(WorkshopStateContract.PUBLISHED)));
    }

    @Test
    void happyPath_verifiesAndPersists() {
        Registration registration = registeredForWorkshop();
        RegistrationId id = registration.id();
        when(registrationRepository.loadById(id)).thenReturn(Optional.of(registration));
        when(registrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubOpenWorkshop();

        handler().handle(new VerifyRegistrationCommand(id.value(), VERIFIER_ID, VERIFIER_ROLE));

        ArgumentCaptor<Registration> savedCaptor = ArgumentCaptor.forClass(Registration.class);
        verify(registrationRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().id()).isEqualTo(id);
        assertThat(savedCaptor.getValue().state()).isEqualTo(RegistrationState.VERIFIED);
        assertThat(savedCaptor.getValue().verifiedAt()).isEqualTo(NOW);

        verify(registrationDomainEventPublisher).publish(argThat(events -> events.size() == 2
                && events.get(1) instanceof RegistrationVerified));
    }

    @Test
    void allowsVerificationWhenWorkshopInProgress() {
        Registration registration = registeredForWorkshop();
        when(registrationRepository.loadById(registration.id())).thenReturn(Optional.of(registration));
        when(workshopExposeApi.getScheduling(WORKSHOP_ID))
                .thenReturn(Optional.of(workshop(WorkshopStateContract.IN_PROGRESS)));

        handler().handle(new VerifyRegistrationCommand(registration.id().value(), VERIFIER_ID, VERIFIER_ROLE));

        ArgumentCaptor<Registration> savedCaptor = ArgumentCaptor.forClass(Registration.class);
        verify(registrationRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().verifiedAt()).isEqualTo(NOW);
        verify(registrationDomainEventPublisher).publish(any());
    }

    @Test
    void rejectsNonVerifierRole() {
        assertThatThrownBy(() -> handler().handle(
                new VerifyRegistrationCommand(UUID.randomUUID(), VERIFIER_ID, "TRAINER")))
                .isInstanceOf(RegistrationRoleViolationException.class);

        verifyNoInteractions(registrationDomainEventPublisher);
    }

    @Test
    void rejectsWhenRegistrationNotFound() {
        UUID id = UUID.randomUUID();
        when(registrationRepository.loadById(RegistrationId.of(id))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new VerifyRegistrationCommand(id, VERIFIER_ID, VERIFIER_ROLE)))
                .isInstanceOf(RegistrationNotFoundException.class);

        verifyNoInteractions(registrationDomainEventPublisher);
    }

    @Test
    void rejectsWhenWorkshopNotFound() {
        Registration registration = registeredForWorkshop();
        when(registrationRepository.loadById(registration.id())).thenReturn(Optional.of(registration));
        when(workshopExposeApi.getScheduling(WORKSHOP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(
                new VerifyRegistrationCommand(registration.id().value(), VERIFIER_ID, VERIFIER_ROLE)))
                .isInstanceOf(RegistrationNotFoundException.class);

        verifyNoInteractions(registrationDomainEventPublisher);
    }

    @Test
    void rejectsWorkshopInDraftState() {
        Registration registration = registeredForWorkshop();
        when(registrationRepository.loadById(registration.id())).thenReturn(Optional.of(registration));
        when(workshopExposeApi.getScheduling(WORKSHOP_ID))
                .thenReturn(Optional.of(workshop(WorkshopStateContract.DRAFT)));

        assertThatThrownBy(() -> handler().handle(
                new VerifyRegistrationCommand(registration.id().value(), VERIFIER_ID, VERIFIER_ROLE)))
                .isInstanceOf(WorkshopNotVerifiableException.class);

        verifyNoInteractions(registrationDomainEventPublisher);
    }

    @Test
    void rejectsWorkshopInPlannedState() {
        Registration registration = registeredForWorkshop();
        when(registrationRepository.loadById(registration.id())).thenReturn(Optional.of(registration));
        when(workshopExposeApi.getScheduling(WORKSHOP_ID))
                .thenReturn(Optional.of(workshop(WorkshopStateContract.PLANNED)));

        assertThatThrownBy(() -> handler().handle(
                new VerifyRegistrationCommand(registration.id().value(), VERIFIER_ID, VERIFIER_ROLE)))
                .isInstanceOf(WorkshopNotVerifiableException.class);

        verifyNoInteractions(registrationDomainEventPublisher);
    }

    @Test
    void rejectsWorkshopInCompletedState() {
        Registration registration = registeredForWorkshop();
        when(registrationRepository.loadById(registration.id())).thenReturn(Optional.of(registration));
        when(workshopExposeApi.getScheduling(WORKSHOP_ID))
                .thenReturn(Optional.of(workshop(WorkshopStateContract.COMPLETED)));

        assertThatThrownBy(() -> handler().handle(
                new VerifyRegistrationCommand(registration.id().value(), VERIFIER_ID, VERIFIER_ROLE)))
                .isInstanceOf(WorkshopNotVerifiableException.class);

        verifyNoInteractions(registrationDomainEventPublisher);
    }

    @Test
    void rejectsWorkshopInCancelledState() {
        Registration registration = registeredForWorkshop();
        when(registrationRepository.loadById(registration.id())).thenReturn(Optional.of(registration));
        when(workshopExposeApi.getScheduling(WORKSHOP_ID))
                .thenReturn(Optional.of(workshop(WorkshopStateContract.CANCELLED)));

        assertThatThrownBy(() -> handler().handle(
                new VerifyRegistrationCommand(registration.id().value(), VERIFIER_ID, VERIFIER_ROLE)))
                .isInstanceOf(WorkshopNotVerifiableException.class);

        verifyNoInteractions(registrationDomainEventPublisher);
    }

    @Test
    void rejectsVerificationOfCancelledRegistration() {
        Registration registration = registeredForWorkshop();
        registration.cancel(START.minus(Registration.CANCELLATION_DEADLINE).minusSeconds(1));
        when(registrationRepository.loadById(registration.id())).thenReturn(Optional.of(registration));
        stubOpenWorkshop();

        assertThatThrownBy(() -> handler().handle(
                new VerifyRegistrationCommand(registration.id().value(), VERIFIER_ID, VERIFIER_ROLE)))
                .isInstanceOf(InvalidRegistrationStateException.class);

        verifyNoInteractions(registrationDomainEventPublisher);
    }

    @Test
    void verifyIsIdempotentForAlreadyVerifiedSeat() {
        Registration registration = registeredForWorkshop();
        registration.verify(NOW.minusSeconds(300));
        registration.clearDomainEvents();
        when(registrationRepository.loadById(registration.id())).thenReturn(Optional.of(registration));
        stubOpenWorkshop();

        handler().handle(new VerifyRegistrationCommand(registration.id().value(), VERIFIER_ID, VERIFIER_ROLE));

        ArgumentCaptor<Registration> savedCaptor = ArgumentCaptor.forClass(Registration.class);
        verify(registrationRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().verifiedAt()).isEqualTo(NOW.minusSeconds(300));
        verify(registrationDomainEventPublisher).publish(argThat(events -> events.isEmpty()));
    }
}
