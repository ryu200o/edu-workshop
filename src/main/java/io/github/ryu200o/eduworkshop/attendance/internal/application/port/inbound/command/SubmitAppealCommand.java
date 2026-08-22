package io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Submits a student appeal — request + <strong>evidence</strong> — during the Reconciliation Window
 * (state {@code RECONCILING}). The appeal <em>never</em> changes {@code currentResult}: it only
 * appends the request and evidence to the append-only ledger for auditor review. Only
 * {@code auditorAdjust()} is an authoritative mutation (ADR 0019 §5).
 *
 * <p>Both {@code reason} and {@code evidenceReference} are mandatory — an appeal without evidence is
 * not a valid appeal (Domain Discovery Round 2). Violation → {@link IllegalArgumentException} → HTTP 400.</p>
 */
public record SubmitAppealCommand(
        UUID recordId,
        String reason,
        String evidenceReference,
        UUID actorId
) implements Command {

    public SubmitAppealCommand {
        if (actorId == null) {
            throw new IllegalArgumentException("actorId must not be null.");
        }
    }
}