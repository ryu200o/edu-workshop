package io.github.ryu200o.eduworkshop.iam.contract.events;

import java.util.UUID;

/**
 * Cross-module integration event emitted (via the outbox) when a new user account is created
 * (self-registration or admin create-user). Carries only the identity + normalized email — never
 * credential material (ADR 0020 §1.6). A future notification module consumes it; the verify token
 * is a dev seam and deliberately not part of the contract (plan §3).
 */
public record UserRegisteredIntegrationEvent(
        UUID userId,
        String email
) implements UserIntegrationEvent {
}