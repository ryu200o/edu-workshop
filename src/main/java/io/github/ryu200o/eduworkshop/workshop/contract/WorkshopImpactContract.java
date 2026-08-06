package io.github.ryu200o.eduworkshop.workshop.contract;

import java.util.UUID;

/**
 * Consumer-driven DTO surfacing a workshop's id and lifecycle state to upper-layer modules (e.g.
 * FacilityOps) for maintenance impact analysis — which workshops overlap a room maintenance window
 * and whether they are {@code PUBLISHED} or {@code PLANNED}. The workshop module maps its internal
 * state into {@link WorkshopStateContract} at the facade; no Workshop {@code internal} type leaks
 * across the module boundary (per ADR 0010).
 *
 * <p>Task-tailored per ADR 0017: carries only {@code id} + {@code state} — exactly what the impact
 * analysis consumes (id to count affected students via registrations, state to classify impact).
 * Eviction-notice data ({@code isRoomEvicted}/{@code roomEvictedAt}) is a different business concern
 * (maintenance already scheduled, not a candidate-window preview) and is deliberately not part of
 * this contract.</p>
 *
 * @param id    the workshop id
 * @param state the workshop lifecycle state (cross-module contract enum)
 */
public record WorkshopImpactContract(
        UUID id,
        WorkshopStateContract state
) {
}
