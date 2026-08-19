package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Revokes every active refresh token of the authenticated caller across all devices
 * ({@code POST /api/v1/iam/me/logout-all}, RFC 6819 family protection). The caller id comes from the
 * JWT principal. Idempotent — revoking an already-empty session is a no-op success.
 *
 * @param userId the authenticated caller
 */
public record LogoutAllCommand(UUID userId) implements Command<LogoutAllCommand.Result> {

    public record Result() {
    }
}