package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopIdView;

import java.time.Instant;
import java.util.List;

/**
 * Query for the ids of every {@code PUBLISHED} workshop whose start time has passed ({@code startTime <= now}).
 * Consumed by the lifecycle job (D1 auto-start). Returns only {@link WorkshopIdView} — the caller needs
 * just the identity to dispatch a {@code StartWorkshopCommand}. Side-effect free.
 */
public record GetWorkshopsDueToStartQuery(
        Instant now
) implements Query<List<WorkshopIdView>> {
}
