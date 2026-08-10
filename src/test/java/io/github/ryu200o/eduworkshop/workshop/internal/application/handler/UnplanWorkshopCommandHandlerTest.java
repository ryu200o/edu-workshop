package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.UnplanWorkshopCommand;
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
class UnplanWorkshopCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-07-23T10:00:00Z");
    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant END = Instant.parse("2026-09-01T11:00:00Z");
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID WORKSHOP_ID = UUID.randomUUID();

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private WorkshopDomainEventPublisher workshopDomainEventPublisher;

    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    private UnplanWorkshopCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UnplanWorkshopCommandHandler(
                workshopRepository, workshopDomainEventPublisher, fixedClock);
    }

    private Workshop createPlannedWorkshop() {
        Workshop workshop = Workshop.create(
                WorkshopId.of(WORKSHOP_ID),
                WorkshopTitle.of("Planned Workshop"),
                WorkshopDescription.of("Description"),
                START, END,
                START.minus(Duration.ofMinutes(15)),
                WorkshopCapacity.of(30),
                NOW
        );
        workshop.plan(RoomReference.of(ROOM_ID, "Room 201", "Building A/2", 50), true,
                workshop.occupancyStart(), NOW);
        return workshop;
    }

    @Test
    void unplan_okReturnsToDraftAndReleasesRoom() {
        Workshop workshop = createPlannedWorkshop();
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));

        UnplanWorkshopCommand.Result result = handler.handle(new UnplanWorkshopCommand(WORKSHOP_ID));

        assertThat(result.id()).isEqualTo(WORKSHOP_ID);
        assertThat(result.updatedAt()).isEqualTo(NOW);
        assertThat(workshop.state()).isEqualTo(WorkshopState.DRAFT);
        assertThat(workshop.roomReference()).isNull();
        assertThat(workshop.hasRoomWarning()).isFalse();

        verify(workshopRepository).save(workshop);
        verify(workshopDomainEventPublisher).publish(any());
    }

    @Test
    void unplan_rejectsNonPlanned() {
        Workshop workshop = Workshop.create(
                WorkshopId.of(WORKSHOP_ID),
                WorkshopTitle.of("Draft Workshop"),
                WorkshopDescription.of("Description"),
                START, END,
                START.minus(Duration.ofMinutes(15)),
                WorkshopCapacity.of(30),
                NOW
        );
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));

        assertThatThrownBy(() -> handler.handle(new UnplanWorkshopCommand(WORKSHOP_ID)))
                .isInstanceOf(InvalidWorkshopStateException.class);
    }

    @Test
    void unplan_throwsWorkshopNotFound() {
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new UnplanWorkshopCommand(WORKSHOP_ID)))
                .isInstanceOf(WorkshopNotFoundException.class);
    }
}
