package io.github.ryu200o.eduworkshop.iam.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import java.time.Instant;

/**
 * Domain event emitted when the user's password is changed (self-service change-password or
 * admin/system password reset). Never carries the password material — only the identity + timestamp.
 */
public record PasswordChanged(
        UserId userId,
        Instant occurredAt
) implements UserDomainEvent {
}