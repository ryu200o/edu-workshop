package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CancelWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopLateThreshold;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopState;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCancelled;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopAlreadyStartedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CancelWorkshopCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant END = Instant.parse("2026-09-01T11:00:00Z");
    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID ROOM_ID = UUID.randomUUID();

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private WorkshopDomainEventPublisher workshopDomainEventPublisher;

    @Captor
    private ArgumentCaptor<List<WorkshopDomainEvent>> eventsCaptor;

    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    private CancelWorkshopCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CancelWorkshopCommandHandler(workshopRepository, workshopDomainEventPublisher, fixedClock);
    }

    private Workshop createPublishedWorkshop() {
        Workshop workshop = Workshop.create(WorkshopId.of(WORKSHOP_ID), WorkshopTitle.of("Test Workshop"), WorkshopDescription.of("Description"), START, END, START.minus(Duration.ofMinutes(15)), WorkshopCapacity.of(30), WorkshopLateThreshold.of(900), NOW);
        workshop.plan(RoomReference.of(ROOM_ID, "Room 201", "Building A/2", 50), false,
                workshop.occupancyStart(), NOW);
        workshop.publish(NOW, 50);
        return workshop;
    }

    @Test
    void cancel_movesWorkshopToCancelledAndPublishesEvents() {
        Workshop workshop = createPublishedWorkshop();
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));

        handler.handle(new CancelWorkshopCommand(WORKSHOP_ID));

        assertThat(workshop.state()).isEqualTo(WorkshopState.CANCELLED);

        verify(workshopRepository).save(workshop);
        verify(workshopDomainEventPublisher).publish(eventsCaptor.capture());
        assertThat(eventsCaptor.getValue()).hasAtLeastOneElementOfType(WorkshopCancelled.class);
        assertThat(workshop.recordedEvents()).isEmpty();
    }

    @Test
    void cancel_throwsWhenWorkshopNotFound() {
        given(workshopRepository.loadById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new CancelWorkshopCommand(WORKSHOP_ID)))
                .isInstanceOf(WorkshopNotFoundException.class);
    }

    @Test
    void cancel_throwsWhenWorkshopAlreadyStarted() {
        Workshop workshop = createPublishedWorkshop();
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));

        handler = new CancelWorkshopCommandHandler(
                workshopRepository, workshopDomainEventPublisher,
                Clock.fixed(START.plusSeconds(1), ZoneOffset.UTC));

        assertThatThrownBy(() -> handler.handle(new CancelWorkshopCommand(WORKSHOP_ID)))
                .isInstanceOf(WorkshopAlreadyStartedException.class);
    }
}
