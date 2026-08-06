package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopSummaryView;

import java.time.Instant;
import java.util.List;

/**
 * Query for every {@code IN_PROGRESS} workshop whose end time has passed ({@code endTime <= now}).
 * Consumed by the lifecycle scheduler (D2 auto-complete). Returns lightweight {@link WorkshopSummaryView}
 * projections. Side-effect free.
 */
public record GetWorkshopsInProgressDueToCompleteQuery(
        Instant now
) implements Query<List<WorkshopSummaryView>> {
}