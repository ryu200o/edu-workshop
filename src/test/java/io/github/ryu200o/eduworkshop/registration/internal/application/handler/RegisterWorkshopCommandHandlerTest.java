package io.github.ryu200o.eduworkshop.registration.internal.application.handler;

import io.github.ryu200o.eduworkshop.registration.internal.application.exception.DuplicateRegistrationException;
import io.github.ryu200o.eduworkshop.registration.internal.application.exception.ReferencedWorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.registration.internal.application.exception.WorkshopNotOpenForRegistrationException;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.in.command.RegisterWorkshopCommand;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.out.RegistrationEventPublisher;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.out.RegistrationRepository;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.Registration;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationState;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.StudentId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.WorkshopReference;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.event.RegistrationCreated;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.event.RegistrationReactivated;
import io.github.ryu200o.eduworkshop.workshop.WorkshopExposeAPI;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopRegistrationContract;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopStateContract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterWorkshopCommandHandlerTest {

    @Mock
    private WorkshopExposeAPI workshopExposeApi;

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private RegistrationEventPublisher registrationEventPublisher;

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");
    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private RegisterWorkshopCommandHandler handler() {
        return new RegisterWorkshopCommandHandler(workshopExposeApi, registrationRepository,
                registrationEventPublisher, clock);
    }

    private WorkshopRegistrationContract publishedWorkshop() {
        return new WorkshopRegistrationContract(WORKSHOP_ID, WorkshopStateContract.PUBLISHED, START);
    }

    @Test
    void happyPath_createsAndPersistsNewRegistration() {
        when(workshopExposeApi.findForRegistration(WORKSHOP_ID)).thenReturn(Optional.of(publishedWorkshop()));
        when(registrationRepository.loadByWorkshopAndUser(WORKSHOP_ID, USER_ID)).thenReturn(Optional.empty());
        when(registrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterWorkshopCommand.Result result = handler().handle(new RegisterWorkshopCommand(WORKSHOP_ID, USER_ID));

        assertThat(result.registrationId()).isNotNull();
        assertThat(result.registeredAt()).isEqualTo(NOW);

        verify(registrationRepository).save(argThat(r ->
                r.state() == RegistrationState.REGISTERED
                        && r.studentId().value().equals(USER_ID)
                        && r.workshopReference().startTime().equals(START)));
        verify(registrationEventPublisher).publish(argThat(events -> events.size() == 1
                && events.getFirst() instanceof RegistrationCreated));
    }

    @Test
    void reactivatesPreviouslyCancelledRowOnSamePair() {
        Registration existing = Registration.create(RegistrationId.generate(), StudentId.of(USER_ID),
                WorkshopReference.of(WORKSHOP_ID, START), NOW);
        existing.cancel(START.minus(Registration.CANCELLATION_DEADLINE).minusSeconds(1));

        when(workshopExposeApi.findForRegistration(WORKSHOP_ID)).thenReturn(Optional.of(publishedWorkshop()));
        when(registrationRepository.loadByWorkshopAndUser(WORKSHOP_ID, USER_ID)).thenReturn(Optional.of(existing));
        when(registrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterWorkshopCommand.Result result = handler().handle(new RegisterWorkshopCommand(WORKSHOP_ID, USER_ID));

        assertThat(result.registrationId()).isEqualTo(existing.id().value());
        verify(registrationEventPublisher).publish(argThat(events -> events.size() == 3
                && events.getLast() instanceof RegistrationReactivated));
    }

    @Test
    void rejectsDuplicateActiveRegistration() {
        Registration existing = Registration.create(RegistrationId.generate(), StudentId.of(USER_ID),
                WorkshopReference.of(WORKSHOP_ID, START), NOW);

        when(workshopExposeApi.findForRegistration(WORKSHOP_ID)).thenReturn(Optional.of(publishedWorkshop()));
        when(registrationRepository.loadByWorkshopAndUser(WORKSHOP_ID, USER_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> handler().handle(new RegisterWorkshopCommand(WORKSHOP_ID, USER_ID)))
                .isInstanceOf(DuplicateRegistrationException.class);

        verify(registrationRepository, never()).save(any());
        verifyNoInteractions(registrationEventPublisher);
    }

    @Test
    void rejectsWhenWorkshopNotFound() {
        when(workshopExposeApi.findForRegistration(WORKSHOP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new RegisterWorkshopCommand(WORKSHOP_ID, USER_ID)))
                .isInstanceOf(ReferencedWorkshopNotFoundException.class);

        verifyNoInteractions(registrationRepository, registrationEventPublisher);
    }

    @Test
    void rejectsWhenWorkshopNotPublished() {
        when(workshopExposeApi.findForRegistration(WORKSHOP_ID))
                .thenReturn(Optional.of(new WorkshopRegistrationContract(WORKSHOP_ID, WorkshopStateContract.DRAFT, START)));

        assertThatThrownBy(() -> handler().handle(new RegisterWorkshopCommand(WORKSHOP_ID, USER_ID)))
                .isInstanceOf(WorkshopNotOpenForRegistrationException.class);

        verifyNoInteractions(registrationRepository, registrationEventPublisher);
    }
}
