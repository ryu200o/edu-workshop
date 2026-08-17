package io.github.ryu200o.eduworkshop.iam.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import java.time.Instant;

/**
 * Domain event emitted when a {@code LOCKED} account returns to {@code ACTIVE} via an explicit admin
 * unlock (or successful login that clears the lockout state).
 */
public record UserUnlocked(
        UserId userId,
        Instant occurredAt
) implements UserDomainEvent {
}