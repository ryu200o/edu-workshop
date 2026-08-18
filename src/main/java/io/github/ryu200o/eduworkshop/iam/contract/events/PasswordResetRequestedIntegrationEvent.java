package io.github.ryu200o.eduworkshop.iam.contract.events;

import java.util.UUID;

/**
 * Cross-module integration event emitted (via the outbox) when a password reset token is issued for
 * an existing account. Carries the {@code tokenId} (opaque token identifier) so the notification
 * layer can reference the token in its email — never the raw reset token itself (ADR 0020 §1.6,
 * plan §3).
 */
public record PasswordResetRequestedIntegrationEvent(
        UUID userId,
        String email,
        UUID tokenId
) implements UserIntegrationEvent {
}