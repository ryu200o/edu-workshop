package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

/**
 * Write command for password login (plan §2.1 steps 5-7). Returns a fresh session: an access JWT
 * (15 min) plus an opaque refresh token (7 days, hash persisted).
 *
 * @param email    the login email
 * @param password the raw password
 */
public record LoginCommand(String email, String password) implements Command<LoginCommand.Result> {

    /**
     * @param accessToken       the signed access JWT (Bearer)
     * @param refreshToken      the RAW opaque refresh token (returned once; only its hash is stored)
     * @param expiresInSeconds  access-token lifetime in seconds (client caching hint)
     * @param mustChangePassword whether the caller must change the password before using business APIs
     */
    public record Result(String accessToken, String refreshToken, long expiresInSeconds,
                         boolean mustChangePassword) {
    }
}
