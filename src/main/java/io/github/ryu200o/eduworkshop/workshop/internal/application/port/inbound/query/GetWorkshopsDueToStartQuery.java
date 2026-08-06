package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopSummaryView;

import java.time.Instant;
import java.util.List;

/**
 * Query for every {@code PUBLISHED} workshop whose start time has passed ({@code startTime <= now}).
 * Consumed by the lifecycle scheduler (D1 auto-start). Returns lightweight {@link WorkshopSummaryView}
 * projections. Side-effect free.
 */
public record GetWorkshopsDueToStartQuery(
        Instant now
) implements Query<List<WorkshopSummaryView>> {
}
