package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

/**
 * Write command for refresh-token rotation (RTR, plan §2.3): consumes the presented refresh token
 * and issues a fresh access + refresh pair. Replay of a revoked token revokes the whole family
 * (OQ-3 RESOLVED).
 *
 * @param refreshToken the raw refresh token from a previous issuance
 */
public record RefreshCommand(String refreshToken) implements Command<RefreshCommand.Result> {

    /**
     * @param accessToken       the new signed access JWT (Bearer)
     * @param refreshToken      the NEW raw opaque refresh token (rotation)
     * @param expiresInSeconds  access-token lifetime in seconds
     */
    public record Result(String accessToken, String refreshToken, long expiresInSeconds) {
    }
}
