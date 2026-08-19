package io.github.ryu200o.eduworkshop.shared.security;

import java.util.Set;
import java.util.UUID;

/**
 * Identity of an authenticated caller, populated by the IAM {@code JwtAuthenticationFilter} into the
 * {@code SecurityContext}. Lives in the shared kernel (not in the IAM module) so every business module
 * can read it from {@code @AuthenticationPrincipal} without creating an adapter-level dependency on
 * {@code iam} internals (ADR 0010 / plan §2.5).
 *
 * <p>Contains the opaque {@code userId}, the normalized {@code email}, the <em>global</em> RBAC roles
 * ({@code USER/ADMIN/PLANNER/AUDITOR/VERIFIER}) and the {@code must_change_password} flag. Contextual
 * authority ({@code TRAINER}/{@code STUDENT}) is intentionally absent — it is evaluated dynamically by
 * the consuming modules against internal foreign keys (OQ-2).</p>
 *
 * @param userId              the IAM user id
 * @param email               the normalized login email
 * @param roles               the global RBAC roles
 * @param mustChangePassword  whether the caller must change the password before using business APIs
 */
public record AuthenticatedPrincipal(
        UUID userId,
        String email,
        Set<String> roles,
        boolean mustChangePassword
) {

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
