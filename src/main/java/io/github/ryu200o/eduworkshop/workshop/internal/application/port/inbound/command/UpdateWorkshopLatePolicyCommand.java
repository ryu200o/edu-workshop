package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Updates a workshop's attendance late-policy threshold (Epic 3C — Workshop owns the policy,
 * ADR 0019 §13.1). The threshold is supplied as a {@code "mm:ss"} string (chốt OQ-3C-8).
 *
 * <p>Format rules (validated at the Application edge, before the aggregate): {@code "mm"} alone is
 * allowed and equals {@code "mm:00"}; {@code mm:ss} normalizes to seconds; negative values, invalid
 * formats, {@code ss >= 60}, or values above the 24h ceiling (86400s) are rejected (HTTP 400). The
 * domain then enforces the lifecycle gate (mutable only until {@code IN_PROGRESS}).</p>
 *
 * @param workshopId    the workshop to update
 * @param lateThreshold the new threshold as {@code "mm:ss"} (or {@code "mm"})
 */
public record UpdateWorkshopLatePolicyCommand(
        UUID workshopId,
        String lateThreshold
) implements Command<UpdateWorkshopLatePolicyCommand.Result> {

    public record Result(UUID workshopId, int lateThresholdSeconds) {
    }
}