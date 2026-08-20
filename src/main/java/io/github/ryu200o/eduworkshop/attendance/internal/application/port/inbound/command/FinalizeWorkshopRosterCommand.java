package io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.Actor;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Finalizes every non-finalized attendance record of a workshop once the Reconciliation Window has
 * closed ({@code RECONCILING → FINALIZED}, permanently locked). Triggered by the system (scheduler /
 * recovery job), never by a human actor.
 */
public record FinalizeWorkshopRosterCommand(
        UUID workshopId,
        Actor actor
) implements Command {
}