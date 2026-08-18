package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Self-service password change ({@code POST /api/v1/iam/me/change-password}). Requires knowledge of
 * the current password (wrong {@code currentPassword} → {@code InvalidCredentialsException}, HTTP
 * 401) and is allowed only on an {@code ACTIVE} account. Clears the {@code must_change_password}
 * gate and revokes every active refresh token of the caller (password change = session kill). This
 * endpoint is on the mcp-gate whitelist so a temporary-password user can set a real password here.
 *
 * @param userId          the authenticated caller
 * @param currentPassword the password currently in effect (proof of possession)
 * @param newPassword     the raw new password (BCrypt-hashed by the handler)
 */
public record ChangePasswordCommand(
        UUID userId,
        String currentPassword,
        String newPassword
) implements Command<ChangePasswordCommand.Result> {

    public record Result() {
    }
}