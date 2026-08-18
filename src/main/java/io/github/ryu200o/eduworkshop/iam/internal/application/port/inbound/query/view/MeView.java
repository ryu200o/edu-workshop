package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Read projection (View) for the authenticated caller's own profile ({@code GET /iam/me} and the
 * response of {@code PUT /iam/me/profile}). Task-tailored per ADR 0017: unlike {@link UserSummaryView}
 * it carries the contact/profile fields and the {@code must_change_password} gate, but omits the
 * security counters that only the admin detail needs. Assembled directly by the read adapter
 * (CQRS bypass — no domain aggregate reconstruction).
 *
 * @param id                  the user id
 * @param email               the normalized login email
 * @param fullName            the display name
 * @param phoneNumber         optional contact phone
 * @param studentCode         optional student code
 * @param avatarUrl           optional avatar URL
 * @param status              the account status (PENDING_VERIFICATION / ACTIVE / LOCKED / DISABLED)
 * @param roles               the global RBAC roles
 * @param mustChangePassword  whether the password gate is active
 * @param createdAt           account creation time
 */
public record MeView(
        UUID id,
        String email,
        String fullName,
        String phoneNumber,
        String studentCode,
        String avatarUrl,
        String status,
        Set<String> roles,
        boolean mustChangePassword,
        Instant createdAt
) {
}