package io.github.ryu200o.eduworkshop.workshop.internal.application.scheduler;

import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
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
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopStarted;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit test for the D3 stale catch-up — the single-transaction {@code start() + complete()} path.
 * Proves the aggregate is rushed from {@code PUBLISHED} straight to {@code COMPLETED} with BOTH
 * {@link WorkshopStarted} and {@link WorkshopCompleted} published in one {@code save}/{@code publish}
 * pair (outbox ADR 0011). The whole method is one transaction, so a failure rolls back atomically —
 * a workshop can never be left stuck in {@code IN_PROGRESS} past its end time.
 */
@ExtendWith(MockitoExtension.class)
class WorkshopCatchUpServiceTest {

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

    private WorkshopCatchUpService service;

    @BeforeEach
    void setUp() {
        service = new WorkshopCatchUpService(workshopRepository, workshopDomainEventPublisher);
    }

    private Workshop createPublishedWorkshop() {
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
        return workshop;
    }

    @Test
    void catchUp_startsAndCompletesInOneSavePublishPair() {
        Workshop workshop = createPublishedWorkshop();
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));

        service.catchUp(WORKSHOP_ID, NOW);

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

        assertThatThrownBy(() -> service.catchUp(WORKSHOP_ID, NOW))
                .isInstanceOf(WorkshopNotFoundException.class);
    }

    @Test
    void catchUp_saveFailure_propagatesForAtomicRollback() {
        Workshop workshop = createPublishedWorkshop();
        given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                .willReturn(Optional.of(workshop));
        given(workshopRepository.save(workshop)).willThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.catchUp(WORKSHOP_ID, NOW))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("db down");
    }
}
