package io.github.ryu200o.eduworkshop.workshop.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimal workshop snapshot surfaced to other modules for registration purposes.
 *
 * <p>Consumer-driven (ADR 0010): exposes only what the Registration module needs to (a) validate
 * that a workshop is open for booking ({@code PUBLISHED}) and (b) snapshot the {@code startTime} as
 * the basis for the 24-hour cancellation-deadline invariant. The workshop module maps its internal
 * state into {@link WorkshopStateContract} at the facade — no Workshop {@code internal} type leaks
 * across the module boundary.</p>
 */
public record WorkshopRegistrationContract(
        UUID workshopId,
        WorkshopStateContract state,
        Instant startTime
) {
}
