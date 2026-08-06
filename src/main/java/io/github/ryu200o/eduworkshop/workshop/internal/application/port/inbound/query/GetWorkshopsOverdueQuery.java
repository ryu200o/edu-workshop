package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopSummaryView;

import java.time.Instant;
import java.util.List;

/**
 * Query for every {@code PUBLISHED} workshop that is already overdue ({@code endTime < now}).
 * Consumed by the lifecycle scheduler (D3 stale catch-up). Returns lightweight
 * {@link WorkshopSummaryView} projections. Side-effect free.
 */
public record GetWorkshopsOverdueQuery(
        Instant now
) implements Query<List<WorkshopSummaryView>> {
}