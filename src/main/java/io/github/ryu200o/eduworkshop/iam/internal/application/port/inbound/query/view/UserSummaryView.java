package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view;

import java.util.Set;
import java.util.UUID;

/**
 * Read projection (View) for a single user's summary. Lives on the Query side and is allowed to
 * aggregate/flatten data freely for the consumer (ADR 0017 — task-tailored projections), without any
 * coupling to the write flow. Assembled directly by the read adapter (CQRS bypass — no domain
 * aggregate reconstruction). Shared source for the Module Facade's {@code UserSummarySnapshot} and
 * the self/admin query handlers.
 *
 * @param id       the user id
 * @param email    the normalized login email
 * @param fullName the display name
 * @param avatarUrl optional avatar URL
 * @param status   the account status as a string (PENDING_VERIFICATION / ACTIVE / LOCKED / DISABLED)
 * @param roles    the global RBAC roles
 */
public record UserSummaryView(
        UUID id,
        String email,
        String fullName,
        String avatarUrl,
        String status,
        Set<String> roles
) {
}
