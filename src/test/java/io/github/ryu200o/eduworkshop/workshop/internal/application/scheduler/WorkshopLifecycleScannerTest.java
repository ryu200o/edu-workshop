package io.github.ryu200o.eduworkshop.workshop.internal.application.scheduler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CompleteWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.StartWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopSummaryView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopReader;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit test for the scheduler orchestration (D4 — Hybrid Model). The scanner reads due/overdue
 * workshops through the {@link WorkshopReader}, dispatches auto-transitions through the shared
 * {@link CommandBus} (reusing the Batch 1 handlers), and delegates the stale catch-up to
 * {@link WorkshopCatchUpService} — per-row failures never abort the whole scan.
 */
@ExtendWith(MockitoExtension.class)
class WorkshopLifecycleScannerTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final UUID WORKSHOP_A = UUID.randomUUID();
    private static final UUID WORKSHOP_B = UUID.randomUUID();

    @Mock
    private WorkshopReader workshopReader;

    @Mock
    private WorkshopCatchUpService workshopCatchUpService;

    @Mock
    private CommandBus commandBus;

    private WorkshopLifecycleScanner scanner;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        scanner = new WorkshopLifecycleScanner(workshopReader, workshopCatchUpService, commandBus, fixedClock);
    }

    private static WorkshopSummaryView view(UUID id, String state) {
        return new WorkshopSummaryView(id, "Test Workshop",
                Instant.parse("2026-09-01T09:00:00Z"), Instant.parse("2026-09-01T11:00:00Z"),
                false, null, state);
    }

    @Test
    void autoStartDue_dispatchesStartCommandForEachDue() {
        given(workshopReader.getPublishedDueToStart(NOW))
                .willReturn(List.of(view(WORKSHOP_A, "PUBLISHED"), view(WORKSHOP_B, "PUBLISHED")));
        given(workshopReader.getInProgressDueToComplete(NOW)).willReturn(List.of());
        given(workshopReader.getPublishedOverdueByEndTime(NOW)).willReturn(List.of());

        scanner.scan();

        verify(commandBus).execute(new StartWorkshopCommand(WORKSHOP_A));
        verify(commandBus).execute(new StartWorkshopCommand(WORKSHOP_B));
        verifyNoInteractions(workshopCatchUpService);
    }

    @Test
    void autoStartDue_skipsWorkshopsWithNoDue() {
        given(workshopReader.getPublishedDueToStart(NOW)).willReturn(List.of());
        given(workshopReader.getInProgressDueToComplete(NOW)).willReturn(List.of());
        given(workshopReader.getPublishedOverdueByEndTime(NOW)).willReturn(List.of());

        scanner.scan();

        verifyNoInteractions(commandBus);
    }

    @Test
    void autoCompleteDue_dispatchesCompleteCommandForEachDue() {
        given(workshopReader.getPublishedDueToStart(NOW)).willReturn(List.of());
        given(workshopReader.getInProgressDueToComplete(NOW))
                .willReturn(List.of(view(WORKSHOP_A, "IN_PROGRESS"), view(WORKSHOP_B, "IN_PROGRESS")));
        given(workshopReader.getPublishedOverdueByEndTime(NOW)).willReturn(List.of());

        scanner.scan();

        verify(commandBus).execute(new CompleteWorkshopCommand(WORKSHOP_A));
        verify(commandBus).execute(new CompleteWorkshopCommand(WORKSHOP_B));
        verifyNoInteractions(workshopCatchUpService);
    }

    @Test
    void catchUpOverdue_delegatesToCatchUpServiceWithoutDispatchingCommands() {
        given(workshopReader.getPublishedDueToStart(NOW)).willReturn(List.of());
        given(workshopReader.getInProgressDueToComplete(NOW)).willReturn(List.of());
        given(workshopReader.getPublishedOverdueByEndTime(NOW))
                .willReturn(List.of(view(WORKSHOP_A, "PUBLISHED"), view(WORKSHOP_B, "PUBLISHED")));

        scanner.scan();

        verify(workshopCatchUpService).catchUp(WORKSHOP_A, NOW);
        verify(workshopCatchUpService).catchUp(WORKSHOP_B, NOW);
        verifyNoInteractions(commandBus);
    }

    @Test
    void singleRowFailure_doesNotAbortRemainingRows() {
        given(workshopReader.getPublishedDueToStart(NOW))
                .willReturn(List.of(view(WORKSHOP_A, "PUBLISHED"), view(WORKSHOP_B, "PUBLISHED")));
        given(workshopReader.getInProgressDueToComplete(NOW)).willReturn(List.of());
        given(workshopReader.getPublishedOverdueByEndTime(NOW)).willReturn(List.of());
        doThrow(new WorkshopNotFoundException("id", WORKSHOP_A))
                .when(commandBus).execute(new StartWorkshopCommand(WORKSHOP_A));

        scanner.scan();

        verify(commandBus).execute(new StartWorkshopCommand(WORKSHOP_A));
        verify(commandBus).execute(new StartWorkshopCommand(WORKSHOP_B));
    }

    @Test
    void catchUpSingleRowFailure_doesNotAbortRemainingRows() {
        given(workshopReader.getPublishedDueToStart(NOW)).willReturn(List.of());
        given(workshopReader.getInProgressDueToComplete(NOW)).willReturn(List.of());
        given(workshopReader.getPublishedOverdueByEndTime(NOW))
                .willReturn(List.of(view(WORKSHOP_A, "PUBLISHED"), view(WORKSHOP_B, "PUBLISHED")));
        doThrow(new RuntimeException("db down"))
                .when(workshopCatchUpService).catchUp(WORKSHOP_A, NOW);

        scanner.scan();

        verify(workshopCatchUpService, times(1)).catchUp(WORKSHOP_A, NOW);
        verify(workshopCatchUpService, times(1)).catchUp(WORKSHOP_B, NOW);
    }
}
