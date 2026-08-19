package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Admin unlock ({@code POST /api/v1/iam/admin/users/{id}/unlock}). Lifts an explicit admin lock back
 * to {@code ACTIVE}. Idempotent when not {@code LOCKED}. Does NOT touch refresh tokens (the account
 * was not authenticating while locked).
 *
 * @param userId the account to unlock
 */
public record AdminUnlockUserCommand(UUID userId) implements Command {
}