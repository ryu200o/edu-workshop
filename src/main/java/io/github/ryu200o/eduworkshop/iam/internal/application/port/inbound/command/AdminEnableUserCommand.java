package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Admin re-enable ({@code POST /api/v1/iam/admin/users/{id}/enable}). Reverts a disabled account back
 * to {@code ACTIVE}. Idempotent when not {@code DISABLED}.
 *
 * @param userId the account to enable
 */
public record AdminEnableUserCommand(UUID userId) implements Command {
}