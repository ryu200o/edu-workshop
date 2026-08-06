package io.github.ryu200o.eduworkshop.workshop.internal.adapter.inbound.scheduling;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryBus;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CatchUpWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CompleteWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.StartWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.GetWorkshopsDueToStartQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.GetWorkshopsInProgressDueToCompleteQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.GetWorkshopsOverdueQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopSummaryView;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit test for the scheduler driving adapter (D4 — Hybrid Model). The scanner reads due/overdue
 * workshops through the {@link QueryBus}, dispatches auto-transitions through the shared
 * {@link CommandBus} (reusing the Batch 1 handlers), and dispatches the stale catch-up as a single
 * {@link CatchUpWorkshopCommand} — per-row failures never abort the whole scan.
 */
@ExtendWith(MockitoExtension.class)
class WorkshopLifecycleScannerTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final UUID WORKSHOP_A = UUID.randomUUID();
    private static final UUID WORKSHOP_B = UUID.randomUUID();

    @Mock
    private QueryBus queryBus;

    @Mock
    private CommandBus commandBus;

    private WorkshopLifecycleScanner scanner;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        scanner = new WorkshopLifecycleScanner(queryBus, commandBus, fixedClock);
    }

    private static WorkshopSummaryView view(UUID id, String state) {
        return new WorkshopSummaryView(id, "Test Workshop",
                Instant.parse("2026-09-01T09:00:00Z"), Instant.parse("2026-09-01T11:00:00Z"),
                false, null, state);
    }

    @Test
    void autoStartDue_queriesAndDispatchesStartCommandForEachDue() {
        given(queryBus.execute(new GetWorkshopsDueToStartQuery(NOW)))
                .willReturn(List.of(view(WORKSHOP_A, "PUBLISHED"), view(WORKSHOP_B, "PUBLISHED")));
        given(queryBus.execute(new GetWorkshopsInProgressDueToCompleteQuery(NOW))).willReturn(List.of());
        given(queryBus.execute(new GetWorkshopsOverdueQuery(NOW))).willReturn(List.of());

        scanner.scan();

        verify(commandBus).execute(new StartWorkshopCommand(WORKSHOP_A));
        verify(commandBus).execute(new StartWorkshopCommand(WORKSHOP_B));
    }

    @Test
    void autoStartDue_skipsWorkshopsWithNoDue() {
        given(queryBus.execute(new GetWorkshopsDueToStartQuery(NOW))).willReturn(List.of());
        given(queryBus.execute(new GetWorkshopsInProgressDueToCompleteQuery(NOW))).willReturn(List.of());
        given(queryBus.execute(new GetWorkshopsOverdueQuery(NOW))).willReturn(List.of());

        scanner.scan();

        verifyNoInteractions(commandBus);
    }

    @Test
    void autoCompleteDue_dispatchesCompleteCommandForEachDue() {
        given(queryBus.execute(new GetWorkshopsDueToStartQuery(NOW))).willReturn(List.of());
        given(queryBus.execute(new GetWorkshopsInProgressDueToCompleteQuery(NOW)))
                .willReturn(List.of(view(WORKSHOP_A, "IN_PROGRESS"), view(WORKSHOP_B, "IN_PROGRESS")));
        given(queryBus.execute(new GetWorkshopsOverdueQuery(NOW))).willReturn(List.of());

        scanner.scan();

        verify(commandBus).execute(new CompleteWorkshopCommand(WORKSHOP_A));
        verify(commandBus).execute(new CompleteWorkshopCommand(WORKSHOP_B));
    }

    @Test
    void catchUpOverdue_dispatchesSingleCatchUpCommandForEachOverdue() {
        given(queryBus.execute(new GetWorkshopsDueToStartQuery(NOW))).willReturn(List.of());
        given(queryBus.execute(new GetWorkshopsInProgressDueToCompleteQuery(NOW))).willReturn(List.of());
        given(queryBus.execute(new GetWorkshopsOverdueQuery(NOW)))
                .willReturn(List.of(view(WORKSHOP_A, "PUBLISHED"), view(WORKSHOP_B, "PUBLISHED")));

        scanner.scan();

        verify(commandBus).execute(new CatchUpWorkshopCommand(WORKSHOP_A));
        verify(commandBus).execute(new CatchUpWorkshopCommand(WORKSHOP_B));
    }

    @Test
    void singleRowFailure_doesNotAbortRemainingRows() {
        given(queryBus.execute(new GetWorkshopsDueToStartQuery(NOW)))
                .willReturn(List.of(view(WORKSHOP_A, "PUBLISHED"), view(WORKSHOP_B, "PUBLISHED")));
        given(queryBus.execute(new GetWorkshopsInProgressDueToCompleteQuery(NOW))).willReturn(List.of());
        given(queryBus.execute(new GetWorkshopsOverdueQuery(NOW))).willReturn(List.of());
        doThrow(new WorkshopNotFoundException("id", WORKSHOP_A))
                .when(commandBus).execute(new StartWorkshopCommand(WORKSHOP_A));

        scanner.scan();

        verify(commandBus).execute(new StartWorkshopCommand(WORKSHOP_A));
        verify(commandBus).execute(new StartWorkshopCommand(WORKSHOP_B));
    }

    @Test
    void catchUpSingleRowFailure_doesNotAbortRemainingRows() {
        given(queryBus.execute(new GetWorkshopsDueToStartQuery(NOW))).willReturn(List.of());
        given(queryBus.execute(new GetWorkshopsInProgressDueToCompleteQuery(NOW))).willReturn(List.of());
        given(queryBus.execute(new GetWorkshopsOverdueQuery(NOW)))
                .willReturn(List.of(view(WORKSHOP_A, "PUBLISHED"), view(WORKSHOP_B, "PUBLISHED")));
        doThrow(new RuntimeException("db down"))
                .when(commandBus).execute(new CatchUpWorkshopCommand(WORKSHOP_A));

        scanner.scan();

        verify(commandBus).execute(new CatchUpWorkshopCommand(WORKSHOP_A));
        verify(commandBus).execute(new CatchUpWorkshopCommand(WORKSHOP_B));
    }
}