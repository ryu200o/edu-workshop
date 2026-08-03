package io.github.ryu200o.eduworkshop.workshop.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumer-driven DTO surfacing a workshop's id and lifecycle state to upper-layer modules (e.g.
 * FacilityOps) for impact analysis — e.g. which workshops overlap a room maintenance window. The
 * workshop module maps its internal state into {@link WorkshopStateContract} at the facade; no
 * Workshop {@code internal} type leaks across the module boundary (per ADR 0010).
 *
 * <p>Also carries the eviction notice fields (Titik 2): a workshop flagged with an eviction notice
 * ({@code isRoomEvicted = true}) is expected to be affected by the maintenance window.</p>
 *
 * @param id            the workshop id
 * @param state         the workshop lifecycle state (cross-module contract enum)
 * @param isRoomEvicted whether the workshop carries an eviction notice (maintenance overlap)
 * @param roomEvictedAt when the eviction notice was issued (null when not evicted)
 */
public record WorkshopImpactContract(
        UUID id,
        WorkshopStateContract state,
        boolean isRoomEvicted,
        Instant roomEvictedAt
) {
}
