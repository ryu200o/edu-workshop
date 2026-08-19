package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryBus;
import io.github.ryu200o.eduworkshop.workshop.contract.CountActiveRegistrationsQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.AdjustWorkshopCapacityCommand;
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
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopCapacityBelowRegistrationsException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopCapacityExceedsRoomException;

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
class AdjustWorkshopCapacityCommandHandlerTest {

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

    private AdjustWorkshopCapacityCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AdjustWorkshopCapacityCommandHandler(
                workshopRepository, queryBus, workshopDomainEventPublisher, fixedClock);
    }

    private Workshop createPublishedWorkshop(int capacity) {
        Workshop workshop = Workshop.create(WorkshopId.of(WORKSHOP_ID), WorkshopTitle.of("Test Workshop"), WorkshopDescription.of("Description"), START, END, START.minus(Duration.ofMinutes(15)), WorkshopCapacity.of(capacity), WorkshopLateThreshold.of(900), NOW);
        workshop.plan(RoomReference.of(ROOM_ID, "Room 201", "Building A/2", 50), false,
                workshop.occupancyStart(), NOW);
        workshop.publish(NOW, 50);
        return workshop;
    }

    @Test
    void adjustCapacity_raisesCapacityWhenValid() {
        Workshop workshop = createPublishedWorkshop(30);
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));
        given(queryBus.execute(new CountActiveRegistrationsQuery(WORKSHOP_ID))).willReturn(20);

        handler.handle(
                new AdjustWorkshopCapacityCommand(WORKSHOP_ID, 40));

        assertThat(workshop.capacity().value()).isEqualTo(40);
        assertThat(workshop.state()).isEqualTo(WorkshopState.PUBLISHED);

        verify(workshopRepository).save(workshop);
        verify(workshopDomainEventPublisher).publish(any());
    }

    @Test
    void adjustCapacity_throwsWhenNewCapacityBelowActiveRegistrations() {
        Workshop workshop = createPublishedWorkshop(30);
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));
        given(queryBus.execute(new CountActiveRegistrationsQuery(WORKSHOP_ID))).willReturn(25);

        assertThatThrownBy(() -> handler.handle(new AdjustWorkshopCapacityCommand(WORKSHOP_ID, 20)))
                .isInstanceOf(WorkshopCapacityBelowRegistrationsException.class);
    }

    @Test
    void adjustCapacity_throwsWhenNewCapacityExceedsRoomCapacity() {
        Workshop workshop = createPublishedWorkshop(30);
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));
        given(queryBus.execute(new CountActiveRegistrationsQuery(WORKSHOP_ID))).willReturn(10);

        assertThatThrownBy(() -> handler.handle(new AdjustWorkshopCapacityCommand(WORKSHOP_ID, 60)))
                .isInstanceOf(WorkshopCapacityExceedsRoomException.class);
    }

    @Test
    void adjustCapacity_throwsWhenWorkshopNotFound() {
        given(workshopRepository.loadById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new AdjustWorkshopCapacityCommand(WORKSHOP_ID, 40)))
                .isInstanceOf(WorkshopNotFoundException.class);
    }
}
