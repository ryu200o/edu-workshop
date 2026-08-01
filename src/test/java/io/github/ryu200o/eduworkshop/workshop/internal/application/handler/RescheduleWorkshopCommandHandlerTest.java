package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.RoomConflictException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.RescheduleWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopState;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopTimeRangeException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.RescheduleDeadlineExceededException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class RescheduleWorkshopCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-07-23T10:00:00Z");
    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant END = Instant.parse("2026-09-01T11:00:00Z");
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final Instant NEW_START = START.plus(Duration.ofDays(3));
    private static final Instant NEW_END = NEW_START.plusSeconds(7200);

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private WorkshopDomainEventPublisher workshopDomainEventPublisher;

    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    private RescheduleWorkshopCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RescheduleWorkshopCommandHandler(
                workshopRepository, workshopDomainEventPublisher,
                new PlannedWorkshopKicker(workshopRepository), fixedClock);
    }

    private Workshop createPublishedWorkshop() {
        Workshop workshop = Workshop.create(
                WorkshopId.of(WORKSHOP_ID),
                WorkshopTitle.of("Published Workshop"),
                WorkshopDescription.of("Description"),
                START, END,
                WorkshopCapacity.of(30),
                NOW
        );
        workshop.plan(RoomReference.of(ROOM_ID, "Room 201", "Building A/2", 50), false, NOW);
        workshop.publish(NOW, 50);
        return workshop;
    }

    @Test
    void reschedule_okMovesTimeAndKeepsRoom() {
        Workshop workshop = createPublishedWorkshop();
        given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));
        given(workshopRepository.countOverlapping(ROOM_ID, NEW_START, NEW_END, WorkshopId.of(WORKSHOP_ID)))
                .willReturn(0);
        given(workshopRepository.loadByRoomId(ROOM_ID)).willReturn(List.of());

        RescheduleWorkshopCommand.Result result = handler.handle(
                new RescheduleWorkshopCommand(WORKSHOP_ID, NEW_START, NEW_END));

        assertThat(result.id()).isEqualTo(WORKSHOP_ID);
        assertThat(result.newStartTime()).isEqualTo(NEW_START);
        assertThat(result.newEndTime()).isEqualTo(NEW_END);
        assertThat(result.updatedAt()).isEqualTo(NOW);
        assertThat(workshop.state()).isEqualTo(WorkshopState.PUBLISHED);
        assertThat(workshop.roomReference().roomId()).isEqualTo(ROOM_ID);
        assertThat(workshop.startTime()).isEqualTo(NEW_START);

        verify(workshopRepository).save(workshop);
        verify(workshopDomainEventPublisher).publish(any());
    }

    @Test
    void reschedule_evictsOverlappingPlannedInSameRoom() {
        Workshop workshop = createPublishedWorkshop();
        Workshop planned = Workshop.create(
                WorkshopId.generate(),
                WorkshopTitle.of("Planned"),
                WorkshopDescription.of("Description"),
                NEW_START, NEW_END,
                WorkshopCapacity.of(20),
                NOW
        );
        planned.plan(RoomReference.of(ROOM_ID, "Room 201", "Building A/2", 50), false, NOW);

        given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));
        given(workshopRepository.countOverlapping(ROOM_ID, NEW_START, NEW_END, WorkshopId.of(WORKSHOP_ID)))
                .willReturn(0);
        given(workshopRepository.loadByRoomId(ROOM_ID)).willReturn(List.of(planned));

        handler.handle(new RescheduleWorkshopCommand(WORKSHOP_ID, NEW_START, NEW_END));

        assertThat(planned.state()).isEqualTo(WorkshopState.DRAFT);
        assertThat(planned.roomReference()).isNotNull();
        verify(workshopRepository).save(planned);
    }

    @Test
    void reschedule_rejectsWhenAnotherPublishedOverlaps() {
        Workshop workshop = createPublishedWorkshop();
        given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));
        given(workshopRepository.countOverlapping(ROOM_ID, NEW_START, NEW_END, WorkshopId.of(WORKSHOP_ID)))
                .willReturn(1);

        assertThatThrownBy(() -> handler.handle(
                new RescheduleWorkshopCommand(WORKSHOP_ID, NEW_START, NEW_END)))
                .isInstanceOf(RoomConflictException.class);
    }

    @Test
    void reschedule_rejectsAfterDeadline() {
        Workshop workshop = createPublishedWorkshop();
        given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));
        given(workshopRepository.countOverlapping(ROOM_ID, NEW_START, NEW_END, WorkshopId.of(WORKSHOP_ID)))
                .willReturn(0);

        Instant within24h = START.minus(Duration.ofHours(12));
        RescheduleWorkshopCommandHandler lateHandler = new RescheduleWorkshopCommandHandler(
                workshopRepository, workshopDomainEventPublisher,
                new PlannedWorkshopKicker(workshopRepository),
                Clock.fixed(within24h, ZoneOffset.UTC));

        assertThatThrownBy(() -> lateHandler.handle(
                new RescheduleWorkshopCommand(WORKSHOP_ID, NEW_START, NEW_END)))
                .isInstanceOf(RescheduleDeadlineExceededException.class)
                .satisfies(e -> assertThat(((RescheduleDeadlineExceededException) e).getDeadline())
                        .isEqualTo(START.minus(Duration.ofHours(24))));
    }

    @Test
    void reschedule_rejectsInvalidTimeWindow() {
        Workshop workshop = createPublishedWorkshop();
        given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));
        given(workshopRepository.countOverlapping(ROOM_ID, NEW_START, NEW_START, WorkshopId.of(WORKSHOP_ID)))
                .willReturn(0);

        assertThatThrownBy(() -> handler.handle(
                new RescheduleWorkshopCommand(WORKSHOP_ID, NEW_START, NEW_START)))
                .isInstanceOf(InvalidWorkshopTimeRangeException.class);
    }

    @Test
    void reschedule_throwsWorkshopNotFound() {
        given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(
                new RescheduleWorkshopCommand(WORKSHOP_ID, NEW_START, NEW_END)))
                .isInstanceOf(WorkshopNotFoundException.class);
    }
}
