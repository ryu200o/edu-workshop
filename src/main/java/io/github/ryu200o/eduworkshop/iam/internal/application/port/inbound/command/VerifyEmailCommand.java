package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

/**
 * Write command that consumes a one-time verify-email token to activate a
 * {@code PENDING_VERIFICATION} account (plan §2.1 step 4).
 *
 * @param token the raw one-time token received at registration
 */
public record VerifyEmailCommand(String token) implements Command<VerifyEmailCommand.Result> {

    public record Result() {
    }
}
