package io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Finalizes every non-finalized attendance record of a workshop once the Reconciliation Window has
 * closed ({@code RECONCILING → FINALIZED}, permanently locked). Dual-triggered: a manual AUDITOR
 * action (HTTP) or the system scheduler/ recovery job (SYSTEM actor). The handler maps the supplied
 * {@code actorId} to the authoritative {@link #SYSTEM_ACTOR_ID} for the job, otherwise to
 * {@code AUDITOR} — both roles are accepted by the aggregate's finalize guard.
 */
public record FinalizeWorkshopRosterCommand(
        UUID workshopId,
        UUID actorId
) implements Command {

    /**
     * Sentinel actor id for the system scheduler / recovery job. The handler recognizes this value
     * and constructs the {@code SYSTEM} actor, distinguishing it from a manual AUDITOR finalize.
     */
    public static final UUID SYSTEM_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public FinalizeWorkshopRosterCommand {
        if (workshopId == null) {
            throw new IllegalArgumentException("workshopId must not be null.");
        }
        if (actorId == null) {
            throw new IllegalArgumentException("actorId must not be null.");
        }
    }
}