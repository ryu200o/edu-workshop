package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Updates a workshop's attendance late-policy threshold (Epic 3C — Workshop owns the policy,
 * ADR 0019 §13.1). The threshold is supplied directly as a number of seconds.
 *
 * <p>The self-validating {@code WorkshopLateThreshold} VO (range 0..86400) is built at the
 * Application edge; negative values or values above the 24h ceiling are rejected (HTTP 400). The
 * domain then enforces the lifecycle gate (mutable only until {@code IN_PROGRESS}).</p>
 *
 * @param workshopId           the workshop to update
 * @param lateThresholdSeconds the new threshold in seconds (0..86400)
 */
public record UpdateWorkshopLatePolicyCommand(
        UUID workshopId,
        int lateThresholdSeconds
) implements Command<UpdateWorkshopLatePolicyCommand.Result> {

    public record Result(UUID workshopId, int lateThresholdSeconds) {
    }
}