package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryBus;
import io.github.ryu200o.eduworkshop.workshop.contract.CountActiveRegistrationsQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.UpdateWorkshopInfoCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopState;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopStateException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopTitleLockedException;

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
class UpdateWorkshopInfoCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant END = Instant.parse("2026-09-01T11:00:00Z");
    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID ROOM_ID = UUID.randomUUID();

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private QueryBus queryBus;

    @Mock
    private WorkshopDomainEventPublisher workshopDomainEventPublisher;

    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    private UpdateWorkshopInfoCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UpdateWorkshopInfoCommandHandler(
                workshopRepository, queryBus, workshopDomainEventPublisher, fixedClock);
    }

    private Workshop createDraftWorkshop() {
        return Workshop.create(
                WorkshopId.of(WORKSHOP_ID),
                WorkshopTitle.of("Original Title"),
                WorkshopDescription.of("Original Description"),
                START, END,
                START.minus(Duration.ofMinutes(15)),
                WorkshopCapacity.of(30),
                NOW);
    }

    private Workshop createPlannedWorkshop() {
        Workshop workshop = createDraftWorkshop();
        workshop.plan(RoomReference.of(ROOM_ID, "Room 201", "Building A/2", 50), false,
                workshop.occupancyStart(), NOW);
        return workshop;
    }

    private Workshop createPublishedWorkshop() {
        Workshop workshop = createDraftWorkshop();
        workshop.plan(RoomReference.of(ROOM_ID, "Room 201", "Building A/2", 50), false,
                workshop.occupancyStart(), NOW);
        workshop.publish(NOW, 50);
        return workshop;
    }

    @Test
    void updateInfo_draft_updatesTitleAndDescription() {
        Workshop workshop = createDraftWorkshop();
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));
        given(queryBus.execute(new CountActiveRegistrationsQuery(WORKSHOP_ID)))
                .willReturn(0);

        UpdateWorkshopInfoCommand.Result result = handler.handle(
                new UpdateWorkshopInfoCommand(WORKSHOP_ID, "New Title", "New Description"));

        assertThat(result.id()).isEqualTo(WORKSHOP_ID);
        assertThat(result.title()).isEqualTo("New Title");
        assertThat(result.description()).isEqualTo("New Description");
        assertThat(result.updatedAt()).isEqualTo(NOW);
        assertThat(workshop.title().value()).isEqualTo("New Title");
        assertThat(workshop.description().value()).isEqualTo("New Description");
        verify(workshopRepository).save(workshop);
        verify(workshopDomainEventPublisher).publish(any());
    }

    @Test
    void updateInfo_planned_updatesTitleAndDescription() {
        Workshop workshop = createPlannedWorkshop();
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));
        given(queryBus.execute(new CountActiveRegistrationsQuery(WORKSHOP_ID)))
                .willReturn(0);

        handler.handle(new UpdateWorkshopInfoCommand(WORKSHOP_ID, "New Title", "New Description"));

        assertThat(workshop.title().value()).isEqualTo("New Title");
        assertThat(workshop.description().value()).isEqualTo("New Description");
    }

    @Test
    void updateInfo_published_noRegistrations_updatesTitle() {
        Workshop workshop = createPublishedWorkshop();
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));
        given(queryBus.execute(new CountActiveRegistrationsQuery(WORKSHOP_ID)))
                .willReturn(0);

        handler.handle(new UpdateWorkshopInfoCommand(WORKSHOP_ID, "New Title", "New Description"));

        assertThat(workshop.title().value()).isEqualTo("New Title");
        assertThat(workshop.description().value()).isEqualTo("New Description");
    }

    @Test
    void updateInfo_published_withRegistrations_updatesDescriptionOnly() {
        Workshop workshop = createPublishedWorkshop();
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));
        given(queryBus.execute(new CountActiveRegistrationsQuery(WORKSHOP_ID)))
                .willReturn(5);

        handler.handle(new UpdateWorkshopInfoCommand(WORKSHOP_ID, "Original Title", "New Description"));

        assertThat(workshop.title().value()).isEqualTo("Original Title");
        assertThat(workshop.description().value()).isEqualTo("New Description");
    }

    @Test
    void updateInfo_published_withRegistrations_rejectsTitleChange() {
        Workshop workshop = createPublishedWorkshop();
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));
        given(queryBus.execute(new CountActiveRegistrationsQuery(WORKSHOP_ID)))
                .willReturn(3);

        assertThatThrownBy(() ->
                handler.handle(new UpdateWorkshopInfoCommand(WORKSHOP_ID, "New Title", "New Description")))
                .isInstanceOf(WorkshopTitleLockedException.class)
                .satisfies(e -> {
                    WorkshopTitleLockedException ex = (WorkshopTitleLockedException) e;
                    assertThat(ex.getWorkshopId().value()).isEqualTo(WORKSHOP_ID);
                    assertThat(ex.getActiveRegistrations()).isEqualTo(3);
                });
    }

    @Test
    void updateInfo_cancelled_isRejected() {
        Workshop workshop = createPublishedWorkshop();
        workshop.cancel(NOW.plusSeconds(1));
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));
        given(queryBus.execute(new CountActiveRegistrationsQuery(WORKSHOP_ID)))
                .willReturn(0);

        assertThatThrownBy(() ->
                handler.handle(new UpdateWorkshopInfoCommand(WORKSHOP_ID, "New Title", "New Description")))
                .isInstanceOf(InvalidWorkshopStateException.class);
    }

    @Test
    void updateInfo_throwsWorkshopNotFound() {
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                handler.handle(new UpdateWorkshopInfoCommand(WORKSHOP_ID, "New Title", "New Description")))
                .isInstanceOf(WorkshopNotFoundException.class);
    }
}