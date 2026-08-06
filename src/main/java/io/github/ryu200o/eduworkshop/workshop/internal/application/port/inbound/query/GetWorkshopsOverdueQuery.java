package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopIdView;

import java.time.Instant;
import java.util.List;

/**
 * Query for the ids of every {@code PUBLISHED} workshop that is already overdue ({@code endTime < now}).
 * Consumed by the lifecycle job (D3 stale catch-up). Returns only {@link WorkshopIdView} — the caller
 * needs just the identity to dispatch a {@code CatchUpWorkshopCommand}. Side-effect free.
 */
public record GetWorkshopsOverdueQuery(
        Instant now
) implements Query<List<WorkshopIdView>> {
}