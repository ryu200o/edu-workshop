package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Admin password reset ({@code POST /api/v1/iam/admin/users/{id}/reset-password}). Sets a new
 * password without proving the old one, in any account state, and forces the mcp gate on so the user
 * must set a personal password on next login. All active refresh tokens are revoked. Invalid (blank)
 * {@code newPassword} → 400.
 *
 * @param userId      the account to reset
 * @param newPassword the raw new password (BCrypt-hashed by the handler)
 */
public record AdminResetPasswordCommand(
        UUID userId,
        String newPassword
) implements Command<AdminResetPasswordCommand.Result> {

    public record Result() {
    }
}