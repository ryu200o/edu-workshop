package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.Set;
import java.util.UUID;

/**
 * Admin role replacement ({@code PUT /api/v1/iam/admin/users/{id}/roles}). Replaces the global role
 * set in one call. The base role {@code USER} is mandatory and enforced by the aggregate's
 * {@code updateRoles} — omitting it yields a 400. Invalid role names yield a 400 as well.
 *
 * @param userId the account to update
 * @param roles  the full new role set (must include {@code USER})
 */
public record AdminUpdateRolesCommand(
        UUID userId,
        Set<String> roles
) implements Command {
}