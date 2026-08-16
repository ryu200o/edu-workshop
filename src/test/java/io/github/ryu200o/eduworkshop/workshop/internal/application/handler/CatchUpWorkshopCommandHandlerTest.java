package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CatchUpWorkshopCommand;
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
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCompleted;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopStarted;

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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit test for the D3 stale catch-up — the single-transaction {@code start() + complete()} command.
 * Proves the aggregate is rushed from {@code PUBLISHED} straight to {@code COMPLETED} with BOTH
 * {@link WorkshopStarted} and {@link WorkshopCompleted} published in one {@code save}/{@code publish}
 * pair (outbox ADR 0011). {@code @Transactional} on {@code handle()} (enforced via the bus proxy)
 * makes the whole transition atomic — a workshop can never be left stuck in {@code IN_PROGRESS}.
 */
@ExtendWith(MockitoExtension.class)
class CatchUpWorkshopCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
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

    private CatchUpWorkshopCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CatchUpWorkshopCommandHandler(
                workshopRepository, workshopDomainEventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Workshop createPublishedWorkshop() {
        Workshop workshop = Workshop.create(WorkshopId.of(WORKSHOP_ID), WorkshopTitle.of("Test Workshop"), WorkshopDescription.of("Description"), START, END, START.minus(Duration.ofMinutes(15)), WorkshopCapacity.of(30), WorkshopLateThreshold.of(900), Instant.parse("2026-07-01T00:00:00Z"));
        workshop.plan(RoomReference.of(ROOM_ID, "Room 201", "Building A/2", 50), false,
                workshop.occupancyStart(), Instant.parse("2026-07-02T00:00:00Z"));
        workshop.publish(Instant.parse("2026-07-03T00:00:00Z"), 50);
        return workshop;
    }

    @Test
    void catchUp_startsAndCompletesInOneSavePublishPair() {
        Workshop workshop = createPublishedWorkshop();
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));

        CatchUpWorkshopCommand.Result result = handler.handle(new CatchUpWorkshopCommand(WORKSHOP_ID));

        assertThat(result.id()).isEqualTo(WORKSHOP_ID);
        assertThat(result.caughtUpAt()).isEqualTo(NOW);
        assertThat(result.state()).isEqualTo("COMPLETED");
        assertThat(workshop.state()).isEqualTo(WorkshopState.COMPLETED);
        verify(workshopRepository).save(workshop);
        verify(workshopDomainEventPublisher).publish(eventsCaptor.capture());
        assertThat(eventsCaptor.getValue())
                .hasAtLeastOneElementOfType(WorkshopStarted.class)
                .hasAtLeastOneElementOfType(WorkshopCompleted.class);
        assertThat(workshop.recordedEvents()).isEmpty();
    }

    @Test
    void catchUp_throwsWhenWorkshopNotFound() {
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new CatchUpWorkshopCommand(WORKSHOP_ID)))
                .isInstanceOf(WorkshopNotFoundException.class);
    }

    @Test
    void catchUp_saveFailure_propagatesForAtomicRollback() {
        Workshop workshop = createPublishedWorkshop();
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));
        given(workshopRepository.save(workshop)).willThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> handler.handle(new CatchUpWorkshopCommand(WORKSHOP_ID)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("db down");
    }
}