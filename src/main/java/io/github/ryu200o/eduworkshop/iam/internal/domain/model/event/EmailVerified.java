package io.github.ryu200o.eduworkshop.iam.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import java.time.Instant;

/**
 * Domain event emitted when a {@code PENDING_VERIFICATION} account successfully verifies its email
 * via the one-time token and becomes {@code ACTIVE}.
 */
public record EmailVerified(
        UserId userId,
        Instant occurredAt
) implements UserDomainEvent {
}