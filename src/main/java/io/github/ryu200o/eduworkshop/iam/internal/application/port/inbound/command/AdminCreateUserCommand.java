package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.Set;
import java.util.UUID;

/**
 * Admin user creation ({@code POST /api/v1/iam/admin/users}, OQ-4 RESOLVED). The account is created
 * {@code ACTIVE} immediately (no {@code PENDING_VERIFICATION}) with {@code must_change_password =
 * true} and a temporary password supplied by the admin. The 48h temporary-password TTL is an
 * Application-layer policy deferred for a later slice (plan §8 risk #6) — the aggregate only enforces
 * the mcp gate. Base role {@code USER} is always present; {@code roles} may carry extra global roles.
 *
 * @param email            the login email (LOWER-normalized by the domain)
 * @param fullName         the display name
 * @param temporaryPassword the temporary password the account starts with (must not be blank)
 * @param roles            optional extra global roles (e.g. ADMIN, PLANNER); {@code USER} is always kept
 */
public record AdminCreateUserCommand(
        String email,
        String fullName,
        String temporaryPassword,
        Set<String> roles
) implements Command<AdminCreateUserCommand.Result> {

    /**
     * @param userId the id minted for the new account
     */
    public record Result(UUID userId) {
    }
}