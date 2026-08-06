package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Command to rush a stale {@code PUBLISHED} workshop whose end time has already passed through
 * {@code start()} then {@code complete()} within a SINGLE transaction (Epic 1, D3 stale catch-up).
 *
 * <p>Modeled as one command (not two separate {@code StartWorkshopCommand} +
 * {@code CompleteWorkshopCommand} dispatches) so the two state transitions, the single {@code save}
 * and the publication of BOTH {@code WorkshopStarted} and {@code WorkshopCompleted} share one
 * transaction scope (outbox, ADR 0011). A workshop can never be left stuck in {@code IN_PROGRESS}
 * past its end time.</p>
 */
public record CatchUpWorkshopCommand(
        UUID workshopId
) implements Command<CatchUpWorkshopCommand.Result> {

    public record Result(UUID id, Instant caughtUpAt, String state) {
    }
}
