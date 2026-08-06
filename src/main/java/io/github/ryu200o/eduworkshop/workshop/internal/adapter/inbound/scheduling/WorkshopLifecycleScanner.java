package io.github.ryu200o.eduworkshop.workshop.internal.adapter.inbound.scheduling;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryBus;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CatchUpWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CompleteWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.StartWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.GetWorkshopsDueToStartQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.GetWorkshopsInProgressDueToCompleteQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.GetWorkshopsOverdueQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopSummaryView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Driving (inbound) scheduling adapter for the Workshop module — the Clock/Timer is a Driving Actor
 * on the same "north side" as an HTTP client. Runs periodically (fixed delay) and performs three
 * time-based transitions (Epic 1, D4 — Hybrid Model):
 *
 * <ol>
 *   <li>{@link #autoStartDue(Instant)} — auto-starts {@code PUBLISHED} workshops whose start time
 *       has passed (D1). Reads candidates through the {@link QueryBus}
 *       ({@link GetWorkshopsDueToStartQuery}), then dispatches {@link StartWorkshopCommand} through the
 *       shared {@link CommandBus} (reuses the Batch 1 handler).</li>
 *   <li>{@link #autoCompleteDue(Instant)} — auto-completes {@code IN_PROGRESS} workshops whose end
 *       time has passed (D2). Reads via {@link GetWorkshopsInProgressDueToCompleteQuery}, dispatches
 *       {@link CompleteWorkshopCommand}.</li>
 *   <li>{@link #catchUpOverdue(Instant)} — stale catch-up (D3): a workshop still {@code PUBLISHED}
 *       after its end time is rushed through {@code start()} then {@code complete()} within a SINGLE
 *       transaction, dispatched as one {@link CatchUpWorkshopCommand} via the {@link CommandBus}
 *       (handled atomically by {@code CatchUpWorkshopCommandHandler}).</li>
 * </ol>
 *
 * <p>As a true inbound adapter it talks exclusively to the shared {@link QueryBus} and {@link CommandBus}
 * — it never reaches into an outbound port directly. Every per-workshop failure is caught and logged so
 * a single bad row never interrupts the {@code @Scheduled} run. Multi-instance deployment (ShedLock /
 * advisory lock) is out of scope for Epic 1 — single-instance assumption only.</p>
 */
@Component
class WorkshopLifecycleScanner {

    private static final Logger log = LoggerFactory.getLogger(WorkshopLifecycleScanner.class);

    private final QueryBus queryBus;
    private final CommandBus commandBus;
    private final Clock clock;

    WorkshopLifecycleScanner(QueryBus queryBus, CommandBus commandBus, Clock clock) {
        this.queryBus = queryBus;
        this.commandBus = commandBus;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.workshop.lifecycle.scan-interval-ms:60000}")
    public void scan() {
        Instant now = Instant.now(clock);
        autoStartDue(now);
        autoCompleteDue(now);
        catchUpOverdue(now);
    }

    private void autoStartDue(Instant now) {
        List<WorkshopSummaryView> due = queryBus.execute(new GetWorkshopsDueToStartQuery(now));
        for (WorkshopSummaryView view : due) {
            try {
                commandBus.execute(new StartWorkshopCommand(view.id()));
                log.info("Auto-started workshop {}", view.id());
            } catch (Exception e) {
                log.warn("Auto-start skipped for workshop {}: {}", view.id(), e.getMessage());
            }
        }
    }

    private void autoCompleteDue(Instant now) {
        List<WorkshopSummaryView> due = queryBus.execute(new GetWorkshopsInProgressDueToCompleteQuery(now));
        for (WorkshopSummaryView view : due) {
            try {
                commandBus.execute(new CompleteWorkshopCommand(view.id()));
                log.info("Auto-completed workshop {}", view.id());
            } catch (Exception e) {
                log.warn("Auto-complete skipped for workshop {}: {}", view.id(), e.getMessage());
            }
        }
    }

    private void catchUpOverdue(Instant now) {
        List<WorkshopSummaryView> overdue = queryBus.execute(new GetWorkshopsOverdueQuery(now));
        for (WorkshopSummaryView view : overdue) {
            try {
                commandBus.execute(new CatchUpWorkshopCommand(view.id()));
                log.info("Caught up overdue workshop {} (started + completed)", view.id());
            } catch (Exception e) {
                log.warn("Catch-up skipped for workshop {}: {}", view.id(), e.getMessage());
            }
        }
    }
}