package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.UpdateWorkshopLatePolicyCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopLateThreshold;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopLatePolicyUpdated;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopStateException;

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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateWorkshopLatePolicyCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private WorkshopDomainEventPublisher workshopDomainEventPublisher;

    private UpdateWorkshopLatePolicyCommandHandler handler() {
        return new UpdateWorkshopLatePolicyCommandHandler(
                workshopRepository, workshopDomainEventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Workshop publishedWorkshop(UUID id) {
        Workshop workshop = Workshop.create(
                WorkshopId.of(id),
                WorkshopTitle.of("Test Workshop"),
                WorkshopDescription.of("Description"),
                NOW.plusSeconds(3600),
                NOW.plusSeconds(7200),
                NOW.plusSeconds(3540),
                WorkshopCapacity.of(30),
                WorkshopLateThreshold.of(900),
                NOW);
        workshop.plan(RoomReference.of(UUID.randomUUID(), "Room 201", "Floor 2", 50), false,
                NOW.plusSeconds(3540), NOW);
        workshop.publish(NOW, 50);
        workshop.clearDomainEvents();
        return workshop;
    }

    @Test
    void happyPath_updatesPolicyAndPublishesEvent() {
        UUID id = UUID.randomUUID();
        Workshop workshop = publishedWorkshop(id);
        when(workshopRepository.loadById(WorkshopId.of(id))).thenReturn(Optional.of(workshop));

        handler().handle(new UpdateWorkshopLatePolicyCommand(id, 930));

        assertThat(workshop.lateThreshold().seconds()).isEqualTo(930);
        verify(workshopRepository).save(workshop);
        verify(workshopDomainEventPublisher).publish(argThat(events -> events.size() == 1
                && events.get(0) instanceof WorkshopLatePolicyUpdated
                && ((WorkshopLatePolicyUpdated) events.get(0)).lateThresholdSeconds() == 930));
    }

    @Test
    void zero_allowed() {
        UUID id = UUID.randomUUID();
        Workshop workshop = publishedWorkshop(id);
        when(workshopRepository.loadById(WorkshopId.of(id))).thenReturn(Optional.of(workshop));

        handler().handle(new UpdateWorkshopLatePolicyCommand(id, 0));

        assertThat(workshop.lateThreshold().seconds()).isZero();
    }

    @Test
    void workshopNotFound_throws() {
        UUID id = UUID.randomUUID();
        when(workshopRepository.loadById(WorkshopId.of(id))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new UpdateWorkshopLatePolicyCommand(id, 900)))
                .isInstanceOf(WorkshopNotFoundException.class);

        verifyNoInteractions(workshopDomainEventPublisher);
    }

    @Test
    void frozenState_rejected() {
        UUID id = UUID.randomUUID();
        Workshop workshop = publishedWorkshop(id);
        workshop.start(workshop.startTime());
        when(workshopRepository.loadById(WorkshopId.of(id))).thenReturn(Optional.of(workshop));

        assertThatThrownBy(() -> handler().handle(new UpdateWorkshopLatePolicyCommand(id, 900)))
                .isInstanceOf(InvalidWorkshopStateException.class);

        verifyNoMoreInteractions(workshopDomainEventPublisher);
    }

    @Test
    void overCeiling_rejectedBeforeDomain() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> handler().handle(new UpdateWorkshopLatePolicyCommand(id, 86401)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler().handle(new UpdateWorkshopLatePolicyCommand(id, -1)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(workshopRepository);
        verifyNoInteractions(workshopDomainEventPublisher);
    }
}