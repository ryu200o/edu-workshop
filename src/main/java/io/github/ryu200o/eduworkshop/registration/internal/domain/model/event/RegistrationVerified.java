package io.github.ryu200o.eduworkshop.registration.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.StudentId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a staff verifier confirms a ticket at the door
 * (state REGISTERED → VERIFIED, Epic 3C).
 *
 * <p>Only reachable when the workshop is {@code PUBLISHED} or {@code IN_PROGRESS} (state gate
 * orchestrated by the Application handler) and the registration is in {@code REGISTERED}; re-verify
 * of an already {@code VERIFIED} seat is an idempotent no-op that records nothing.</p>
 */
public record RegistrationVerified(
        RegistrationId registrationId,
        UUID workshopId,
        StudentId studentId,
        Instant verifiedAt
) implements RegistrationDomainEvent {
}