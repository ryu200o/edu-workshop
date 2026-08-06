package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view;

import java.util.UUID;

/**
 * Ultra-light read projection carrying only a workshop's id. Used by consumer-driven queries that
 * need just the identity (e.g. the lifecycle job, which iterates candidate ids to dispatch state
 * transitions). Avoids over-fetching the full {@link WorkshopSummaryView}.
 *
 * @param id the workshop id
 */
public record WorkshopIdView(
        UUID id
) {
}