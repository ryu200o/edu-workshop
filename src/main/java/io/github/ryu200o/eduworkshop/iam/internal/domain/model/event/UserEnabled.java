package io.github.ryu200o.eduworkshop.iam.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import java.time.Instant;

/**
 * Domain event emitted when a disabled account is re-enabled by an admin (back to {@code ACTIVE}).
 */
public record UserEnabled(
        UserId userId,
        Instant occurredAt
) implements UserDomainEvent {
}