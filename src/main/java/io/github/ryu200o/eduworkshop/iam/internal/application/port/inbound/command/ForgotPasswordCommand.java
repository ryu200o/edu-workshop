package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

/**
 * Write command for the forgot-password flow (plan §1.2 line 34): issues a one-time reset token.
 * Responds identically whether or not the email exists to avoid account enumeration.
 *
 * @param email the login email
 */
public record ForgotPasswordCommand(String email) implements Command {
}
