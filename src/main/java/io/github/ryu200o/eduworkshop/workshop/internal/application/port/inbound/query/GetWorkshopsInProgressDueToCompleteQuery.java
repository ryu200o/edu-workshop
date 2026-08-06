package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopIdView;

import java.time.Instant;
import java.util.List;

/**
 * Query for the ids of every {@code IN_PROGRESS} workshop whose end time has passed ({@code endTime <= now}).
 * Consumed by the lifecycle job (D2 auto-complete). Returns only {@link WorkshopIdView} — the caller needs
 * just the identity to dispatch a {@code CompleteWorkshopCommand}. Side-effect free.
 */
public record GetWorkshopsInProgressDueToCompleteQuery(
        Instant now
) implements Query<List<WorkshopIdView>> {
}