package io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Verifies a ticket at the door (Epic 3C): a staff verifier scans the learner's QR and the seat
 * flips {@code REGISTERED → VERIFIED}.
 *
 * <p>The {@code registrationId} is resolved from the scanned {@code qrReference} by the thin QR
 * seam (fixture in Slice A, real resolver in Slice B — OQ-3C-6); the handler never sees the QR
 * itself. {@code verifierId}/{@code role} come from the authenticated principal
 * ({@code X-User-Id}/{@code X-Actor-Role} in Dev/Test); only {@code VERIFIER} passes the role gate
 * (OQ-3C-1).</p>
 */
public record VerifyRegistrationCommand(
        UUID registrationId,
        UUID verifierId,
        String role
) implements Command<VerifyRegistrationCommand.Result> {

    public record Result(UUID registrationId, Instant verifiedAt) {
    }
}