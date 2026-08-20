package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

/**
 * Write command that consumes a one-time reset token and sets a new password. Also revokes all
 * refresh tokens of the account (ADR 0020 §1.4).
 *
 * @param token       the raw one-time reset token
 * @param newPassword the raw new password (BCrypt-hashed by the handler)
 */
public record ResetPasswordCommand(String token, String newPassword)
        implements Command {
}
