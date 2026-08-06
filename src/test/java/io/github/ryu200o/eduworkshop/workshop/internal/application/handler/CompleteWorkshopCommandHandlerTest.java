package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CompleteWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopState;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCompleted;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopStateException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopCompletionNotDueException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
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
class CompleteWorkshopCommandHandlerTest {

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

    private final Clock fixedClock = Clock.fixed(END, ZoneOffset.UTC);

    private CompleteWorkshopCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CompleteWorkshopCommandHandler(workshopRepository, workshopDomainEventPublisher, fixedClock);
    }

    private Workshop createInProgressWorkshop() {
        Workshop workshop = Workshop.create(
                WorkshopId.of(WORKSHOP_ID),
                WorkshopTitle.of("Test Workshop"),
                WorkshopDescription.of("Description"),
                START, END,
                WorkshopCapacity.of(30),
                Instant.parse("2026-07-01T00:00:00Z"));
        workshop.plan(RoomReference.of(ROOM_ID, "Room 201", "Building A/2", 50), false,
                Instant.parse("2026-07-02T00:00:00Z"));
        workshop.publish(Instant.parse("2026-07-03T00:00:00Z"), 50);
        workshop.start(START);   // → IN_PROGRESS
        return workshop;
    }

    @Test
    void complete_movesInProgressToCompletedAndPublishesEvents() {
        Workshop workshop = createInProgressWorkshop();
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));

        CompleteWorkshopCommand.Result result = handler.handle(
                new CompleteWorkshopCommand(WORKSHOP_ID));

        assertThat(result.id()).isEqualTo(WORKSHOP_ID);
        assertThat(result.completedAt()).isEqualTo(END);
        assertThat(result.state()).isEqualTo("COMPLETED");
        assertThat(workshop.state()).isEqualTo(WorkshopState.COMPLETED);

        verify(workshopRepository).save(workshop);
        verify(workshopDomainEventPublisher).publish(eventsCaptor.capture());
        assertThat(eventsCaptor.getValue()).hasAtLeastOneElementOfType(WorkshopCompleted.class);
        assertThat(workshop.recordedEvents()).isEmpty();
    }

    @Test
    void complete_throwsWhenWorkshopNotFound() {
        given(workshopRepository.loadById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new CompleteWorkshopCommand(WORKSHOP_ID)))
                .isInstanceOf(WorkshopNotFoundException.class);
    }

    @Test
    void complete_throwsWhenWorkshopNotInProgress() {
        Workshop workshop = Workshop.create(
                WorkshopId.of(WORKSHOP_ID),
                WorkshopTitle.of("Test Workshop"),
                WorkshopDescription.of("Description"),
                START, END,
                WorkshopCapacity.of(30),
                NOW);
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));

        assertThatThrownBy(() -> handler.handle(new CompleteWorkshopCommand(WORKSHOP_ID)))
                .isInstanceOf(InvalidWorkshopStateException.class);
    }

    @Test
    void complete_throwsWhenEndTimeNotReached() {
        Workshop workshop = createInProgressWorkshop();
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));

        handler = new CompleteWorkshopCommandHandler(
                workshopRepository, workshopDomainEventPublisher,
                Clock.fixed(END.minusSeconds(1), ZoneOffset.UTC));

        assertThatThrownBy(() -> handler.handle(new CompleteWorkshopCommand(WORKSHOP_ID)))
                .isInstanceOf(WorkshopCompletionNotDueException.class);
    }
}