package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view;

import java.util.UUID;

/**
 * Read projection carrying only a workshop's identity and lifecycle state for the maintenance
 * impact-analysis path (which workshops overlap a room maintenance window). Task-tailored per ADR
 * 0017: this is a processing view for FacilityOps impact counting — distinct from the UI display
 * {@link WorkshopSummaryView}. The consumer only needs {@code id} (to count affected students via
 * registrations) and {@code state} (to classify PUBLISHED vs PLANNED), so the adapter selects
 * exactly these two columns (no over-fetch).
 *
 * @param id    the workshop id
 * @param state the lifecycle state (PUBLISHED / PLANNED — only planning-relevant states are returned)
 */
public record WorkshopOverlapView(
        UUID id,
        String state
) {
}
