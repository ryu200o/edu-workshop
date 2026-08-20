package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Admin disable ({@code POST /api/v1/iam/admin/users/{id}/disable}). Disables the account (no
 * time-based auto-recovery; only {@code enable} reverts it) and revokes all its active refresh
 * tokens. Idempotent when already {@code DISABLED}.
 *
 * @param userId the account to disable
 */
public record AdminDisableUserCommand(UUID userId) implements Command {
}