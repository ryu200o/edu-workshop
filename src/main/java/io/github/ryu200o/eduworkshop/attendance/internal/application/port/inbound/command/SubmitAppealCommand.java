package io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.Actor;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Submits a student appeal (request + evidence) for an attendance record during the Reconciliation
 * Window. The appeal never changes {@code currentResult} — it only records the request and evidence
 * on the ledger; only {@code auditorAdjust} is an authoritative mutation (ADR 0019 §5).
 */
public record SubmitAppealCommand(
        UUID recordId,
        String reason,
        String evidenceReference,
        Actor actor
) implements Command<SubmitAppealCommand.Result> {

    public record Result(
            UUID recordId,
            int entryNumber,
            String state,
            String message
    ) {
    }
}