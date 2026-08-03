package io.github.ryu200o.eduworkshop.workshop.contract;

import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopStateContract;

import java.util.UUID;

/**
 * Consumer-driven DTO surfacing a workshop's id and lifecycle state to upper-layer modules (e.g.
 * FacilityOps) for impact analysis — e.g. which workshops overlap a room maintenance window. The
 * workshop module maps its internal state into {@link WorkshopStateContract} at the facade; no
 * Workshop {@code internal} type leaks across the module boundary (per ADR 0010).
 *
 * @param id    the workshop id
 * @param state the workshop lifecycle state (cross-module contract enum)
 */
public record WorkshopImpactDto(
        UUID id,
        WorkshopStateContract state
) {
}
