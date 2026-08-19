package io.github.ryu200o.eduworkshop.iam.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import java.time.Instant;

/**
 * Domain event emitted when a new user account is created (self-registration or admin create-user).
 * Carries only the identity + normalized email — never any credential material.
 */
public record UserRegistered(
        UserId userId,
        Email email,
        Instant occurredAt
) implements UserDomainEvent {
}