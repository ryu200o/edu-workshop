package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.UpdateWorkshopScheduleCommand;
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
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopStateException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UpdateWorkshopScheduleCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant END = Instant.parse("2026-09-01T11:00:00Z");
    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID ROOM_ID = UUID.randomUUID();

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private WorkshopDomainEventPublisher workshopDomainEventPublisher;

    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    private UpdateWorkshopScheduleCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UpdateWorkshopScheduleCommandHandler(
                workshopRepository, workshopDomainEventPublisher, fixedClock);
    }

    private Workshop createDraftWorkshop() {
        return Workshop.create(
                WorkshopId.of(WORKSHOP_ID),
                WorkshopTitle.of("Test Workshop"),
                WorkshopDescription.of("Description"),
                START, END,
                WorkshopCapacity.of(30),
                NOW);
    }

    private Workshop createPlannedWorkshop() {
        Workshop workshop = createDraftWorkshop();
        workshop.plan(RoomReference.of(ROOM_ID, "Room 201", "Building A/2", 50), false, NOW);
        return workshop;
    }

    private Workshop createPublishedWorkshop() {
        Workshop workshop = createDraftWorkshop();
        workshop.plan(RoomReference.of(ROOM_ID, "Room 201", "Building A/2", 50), false, NOW);
        workshop.publish(NOW, 50);
        return workshop;
    }

    private Workshop createCancelledWorkshop() {
        Workshop workshop = createPublishedWorkshop();
        workshop.cancel(NOW.plusSeconds(1));
        return workshop;
    }

    @Test
    void updateSchedule_draft_validRange_updatesTimes() {
        Workshop workshop = createDraftWorkshop();
        given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));

        Instant newStart = START.plus(Duration.ofDays(7));
        Instant newEnd = newStart.plusSeconds(7200);

        UpdateWorkshopScheduleCommand.Result result = handler.handle(
                new UpdateWorkshopScheduleCommand(WORKSHOP_ID, newStart, newEnd));

        assertThat(result.id()).isEqualTo(WORKSHOP_ID);
        assertThat(result.startTime()).isEqualTo(newStart);
        assertThat(result.endTime()).isEqualTo(newEnd);
        assertThat(result.updatedAt()).isEqualTo(NOW);
        assertThat(workshop.startTime()).isEqualTo(newStart);
        assertThat(workshop.endTime()).isEqualTo(newEnd);
        verify(workshopRepository).save(workshop);
        verify(workshopDomainEventPublisher).publish(any());
    }

    @Test
    void updateSchedule_planned_validRange_updatesTimes_keepsRoom() {
        Workshop workshop = createPlannedWorkshop();
        given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));

        Instant newStart = START.plus(Duration.ofDays(7));
        Instant newEnd = newStart.plusSeconds(7200);

        handler.handle(new UpdateWorkshopScheduleCommand(WORKSHOP_ID, newStart, newEnd));

        assertThat(workshop.startTime()).isEqualTo(newStart);
        assertThat(workshop.endTime()).isEqualTo(newEnd);
        assertThat(workshop.roomReference().roomId()).isEqualTo(ROOM_ID);
    }

    @Test
    void updateSchedule_published_isRejected() {
        Workshop workshop = createPublishedWorkshop();
        given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));

        Instant newStart = START.plus(Duration.ofDays(7));
        Instant newEnd = newStart.plusSeconds(7200);

        assertThatThrownBy(() ->
                handler.handle(new UpdateWorkshopScheduleCommand(WORKSHOP_ID, newStart, newEnd)))
                .isInstanceOf(InvalidWorkshopStateException.class)
                .satisfies(e -> {
                    InvalidWorkshopStateException ex = (InvalidWorkshopStateException) e;
                    assertThat(ex.getCurrentState()).isEqualTo(WorkshopState.PUBLISHED);
                });
    }

    @Test
    void updateSchedule_cancelled_isRejected() {
        Workshop workshop = createCancelledWorkshop();
        given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));

        Instant newStart = START.plus(Duration.ofDays(7));
        Instant newEnd = newStart.plusSeconds(7200);

        assertThatThrownBy(() ->
                handler.handle(new UpdateWorkshopScheduleCommand(WORKSHOP_ID, newStart, newEnd)))
                .isInstanceOf(InvalidWorkshopStateException.class);
    }

    @Test
    void updateSchedule_rejectsEndNotAfterStart() {
        Workshop workshop = createDraftWorkshop();
        given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));

        Instant newStart = START.plus(Duration.ofDays(7));
        Instant newEnd = newStart.minusSeconds(1);

        assertThatThrownBy(() ->
                handler.handle(new UpdateWorkshopScheduleCommand(WORKSHOP_ID, newStart, newEnd)))
                .isInstanceOf(InvalidWorkshopTimeRangeException.class)
                .hasMessageContaining("after newStartTime");
    }

    @Test
    void updateSchedule_rejectsStartNotInFuture() {
        Workshop workshop = createDraftWorkshop();
        given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));

        Instant newStart = NOW.minusSeconds(1);
        Instant newEnd = newStart.plusSeconds(7200);

        assertThatThrownBy(() ->
                handler.handle(new UpdateWorkshopScheduleCommand(WORKSHOP_ID, newStart, newEnd)))
                .isInstanceOf(InvalidWorkshopTimeRangeException.class)
                .hasMessageContaining("in the future");
    }

    @Test
    void updateSchedule_throwsWorkshopNotFound() {
        given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.empty());

        Instant newStart = START.plus(Duration.ofDays(7));
        Instant newEnd = newStart.plusSeconds(7200);

        assertThatThrownBy(() ->
                handler.handle(new UpdateWorkshopScheduleCommand(WORKSHOP_ID, newStart, newEnd)))
                .isInstanceOf(WorkshopNotFoundException.class);
    }
}