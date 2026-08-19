package io.github.ryu200o.eduworkshop.iam.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import java.time.Instant;

/**
 * Domain event emitted when an account enters {@code LOCKED} state — either by escalated brute-force
 * lockout ({@code lockoutCount} escalation) or by an explicit admin lock. Carries the escalated
 * {@code lockoutCount} and the lock window end.
 */
public record UserLocked(
        UserId userId,
        int lockoutCount,
        Instant lockedUntil,
        Instant occurredAt
) implements UserDomainEvent {
}