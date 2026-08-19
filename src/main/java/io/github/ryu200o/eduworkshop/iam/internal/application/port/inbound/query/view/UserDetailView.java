package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Read projection (View) for the admin {@code GET /iam/admin/users/{id}} detail endpoint. Superset of
 * the self profile ({@link MeView}): additionally exposes the account-security counters the admin
 * needs to triage lockouts and abuse. Task-tailored per ADR 0017; assembled directly by the read
 * adapter (CQRS bypass). The {@code passwordHash} is intentionally absent — never read back.
 *
 * @param id                   the user id
 * @param email                the normalized login email
 * @param fullName             the display name
 * @param phoneNumber          optional contact phone
 * @param studentCode          optional student code
 * @param avatarUrl            optional avatar URL
 * @param status               the account status (PENDING_VERIFICATION / ACTIVE / LOCKED / DISABLED)
 * @param roles                the global RBAC roles
 * @param mustChangePassword   whether the password gate is active
 * @param createdAt            account creation time
 * @param updatedAt            last mutation time
 * @param failedLoginAttempts  consecutive failed logins since last success
 * @param lockoutCount         escalating lockout offense counter
 * @param lockedUntil          end of the current lock window (null = infinite admin lock)
 * @param lastLockedAt         when the current/last lockout started
 */
public record UserDetailView(
        UUID id,
        String email,
        String fullName,
        String phoneNumber,
        String studentCode,
        String avatarUrl,
        String status,
        Set<String> roles,
        boolean mustChangePassword,
        Instant createdAt,
        Instant updatedAt,
        int failedLoginAttempts,
        int lockoutCount,
        Instant lockedUntil,
        Instant lastLockedAt
) {
}