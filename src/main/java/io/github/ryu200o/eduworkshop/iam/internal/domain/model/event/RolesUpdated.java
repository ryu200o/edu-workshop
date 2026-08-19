package io.github.ryu200o.eduworkshop.iam.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.iam.internal.domain.model.GlobalRole;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import java.time.Instant;
import java.util.Set;

/**
 * Domain event emitted when a user's global roles are changed (admin role update).
 * Captures the previous and new role sets for auditability.
 */
public record RolesUpdated(
        UserId userId,
        Set<GlobalRole> previousRoles,
        Set<GlobalRole> newRoles,
        Instant occurredAt
) implements UserDomainEvent {
}