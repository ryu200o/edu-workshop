package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Admin lockout ({@code POST /api/v1/iam/admin/users/{id}/lock}). Explicitly locks the account with an
 * infinite lock window (no time-based auto-recovery) and revokes all its active refresh tokens.
 * Idempotent when already {@code LOCKED}/{@code DISABLED}.
 *
 * @param userId the account to lock
 */
public record AdminLockUserCommand(UUID userId) implements Command<AdminLockUserCommand.Result> {

    public record Result() {
    }
}