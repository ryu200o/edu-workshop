package io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Verifies a ticket at the door (Epic 3C): a staff verifier scans the learner's QR and the seat
 * flips {@code REGISTERED → VERIFIED}.
 *
 * <p>The {@code registrationId} is resolved from the scanned {@code qrReference} by the thin QR
 * seam (fixture in Slice A, real resolver in Slice B — OQ-3C-6); the handler never sees the QR
 * itself. {@code verifierId} is the authenticated principal's userId. The caller must hold the
 * {@code VERIFIER} global role — this is enforced declaratively by {@code @CanVerifyRegistrations}
 * on the inbound controller (ADR 0023), so the command no longer carries a derived role.</p>
 */
public record VerifyRegistrationCommand(
        UUID registrationId,
        UUID verifierId
) implements Command {
}
