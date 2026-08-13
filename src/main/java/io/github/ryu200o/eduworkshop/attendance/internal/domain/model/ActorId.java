package io.github.ryu200o.eduworkshop.attendance.internal.domain.model;

import java.util.UUID;

/**
 * Identity of the actor who performed an attendance decision (trainer, student, auditor or the
 * system). Derived from the authenticated principal at the Application boundary (ADR 0019 §8).
 */
public record ActorId(UUID value) {

    public ActorId {
        if (value == null) {
            throw new IllegalArgumentException("ActorId must not be null.");
        }
    }

    public static ActorId of(UUID value) {
        return new ActorId(value);
    }
}