package io.github.ryu200o.eduworkshop.workshop.internal.application.scheduler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CatchUpWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CompleteWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.StartWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopSummaryView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Lifecycle scheduler for the Workshop module. Runs periodically (fixed delay) and performs three
 * time-based transitions (Epic 1, D4 — Hybrid Model):
 *
 * <ol>
 *   <li>{@link #autoStartDue(Instant)} — auto-starts {@code PUBLISHED} workshops whose start time
 *       has passed (D1), dispatching {@link StartWorkshopCommand} through the shared
 *       {@link CommandBus} (reuses the Batch 1 handler).</li>
 *   <li>{@link #autoCompleteDue(Instant)} — auto-completes {@code IN_PROGRESS} workshops whose end
 *       time has passed (D2), dispatching {@link CompleteWorkshopCommand}.</li>
 *   <li>{@link #catchUpOverdue(Instant)} — stale catch-up (D3): a workshop still {@code PUBLISHED}
 *       after its end time is rushed through {@code start()} then {@code complete()} within a
 *       SINGLE transaction, dispatched as one {@link CatchUpWorkshopCommand} (handled atomically by
 *       {@code CatchUpWorkshopCommandHandler}).</li>
 * </ol>
 *
 * <p>Every per-workshop failure is caught and logged so a single bad row never interrupts the
 * {@code @Scheduled} run. Multi-instance deployment (ShedLock / advisory lock) is out of scope for
 * Epic 1 — single-instance assumption only.</p>
 */
@Component
class WorkshopLifecycleScanner {

    private static final Logger log = LoggerFactory.getLogger(WorkshopLifecycleScanner.class);

    private final WorkshopReader workshopReader;
    private final CommandBus commandBus;
    private final Clock clock;

    WorkshopLifecycleScanner(WorkshopReader workshopReader,
                             CommandBus commandBus,
                             Clock clock) {
        this.workshopReader = workshopReader;
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
        for (WorkshopSummaryView view : workshopReader.getPublishedDueToStart(now)) {
            try {
                commandBus.execute(new StartWorkshopCommand(view.id()));
                log.info("Auto-started workshop {}", view.id());
            } catch (Exception e) {
                log.warn("Auto-start skipped for workshop {}: {}", view.id(), e.getMessage());
            }
        }
    }

    private void autoCompleteDue(Instant now) {
        for (WorkshopSummaryView view : workshopReader.getInProgressDueToComplete(now)) {
            try {
                commandBus.execute(new CompleteWorkshopCommand(view.id()));
                log.info("Auto-completed workshop {}", view.id());
            } catch (Exception e) {
                log.warn("Auto-complete skipped for workshop {}: {}", view.id(), e.getMessage());
            }
        }
    }

    private void catchUpOverdue(Instant now) {
        for (WorkshopSummaryView view : workshopReader.getPublishedOverdueByEndTime(now)) {
            try {
                commandBus.execute(new CatchUpWorkshopCommand(view.id()));
                log.info("Caught up overdue workshop {} (started + completed)", view.id());
            } catch (Exception e) {
                log.warn("Catch-up skipped for workshop {}: {}", view.id(), e.getMessage());
            }
        }
    }
}
