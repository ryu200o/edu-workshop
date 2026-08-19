package io.github.ryu200o.eduworkshop.iam.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import java.time.Instant;

/**
 * Domain event emitted when an account is disabled by an admin ({@code DISABLED}).
 */
public record UserDisabled(
        UserId userId,
        Instant occurredAt
) implements UserDomainEvent {
}