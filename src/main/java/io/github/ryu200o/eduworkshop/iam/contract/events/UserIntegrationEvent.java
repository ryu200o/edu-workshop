package io.github.ryu200o.eduworkshop.iam.contract.events;

import java.util.UUID;

/**
 * Sealed marker for cross-module integration events emitted by the IAM module (outbox, ADR 0011).
 * Currently published for {@link UserRegisteredIntegrationEvent} (account created — maps from the
 * {@code UserRegistered} domain event) and {@link PasswordResetRequestedIntegrationEvent} (a reset
 * token was issued — published directly by the forgot-password handler). Per plan §3 the payloads
 * carry only {@code userId}/email/tokenId — never raw credential or token material (ADR 0020 §1.6).
 */
public sealed interface UserIntegrationEvent
        permits UserRegisteredIntegrationEvent,
                PasswordResetRequestedIntegrationEvent {

    UUID userId();
}