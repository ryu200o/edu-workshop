package io.github.ryu200o.eduworkshop.attendance.internal.domain.model;

/**
 * The actor who performed an attendance decision. Recorded verbatim on each ledger entry so the
 * Decision Ledger stays fully auditable (who did what and when).
 */
public record Actor(ActorId id, ActorRole role) {

    public Actor {
        if (id == null) {
            throw new IllegalArgumentException("Actor id must not be null.");
        }
        if (role == null) {
            throw new IllegalArgumentException("Actor role must not be null.");
        }
    }
}