package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

/**
 * Logs out the presented refresh token ({@code POST /api/v1/iam/auth/logout}). Only the single
 * presented token is revoked (RFC 6819 scope), by its SHA-256 hash — the raw value is never stored.
 * Idempotent: an unknown or already-revoked token still yields a success (200) so clients can always
 * "log out". No user lookup is required because the token itself identifies its owner. Whitelisted in
 * the mcp gate so a temporary-password user may log out too.
 *
 * @param refreshToken the RAW opaque refresh token to revoke
 */
public record LogoutCommand(String refreshToken) implements Command<LogoutCommand.Result> {

    public record Result() {
    }
}