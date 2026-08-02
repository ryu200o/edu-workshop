package io.github.ryu200o.eduworkshop.registration.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;

import java.time.Instant;

/**
 * Emitted when an active ({@code REGISTERED}) registration is granted (or re-extended) its
 * 12-hour urgent cancellation grace window because the workshop was rescheduled.
 *
 * <p>Carries the registration id, the effective grace-period end instant, and the occurrence time
 * (equal to the {@code updatedAt} at the moment of the grant).</p>
 */
public record RegistrationGracePeriodGranted(
        RegistrationId registrationId,
        Instant gracePeriodUntil,
        Instant occurredAt
) implements RegistrationDomainEvent {
}