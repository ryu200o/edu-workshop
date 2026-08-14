package io.github.ryu200o.eduworkshop.attendance.internal.domain.model;

import java.util.UUID;

/**
 * Identity of the learner whose attendance is being recorded.
 *
 * <p>Per the Registration Verification Dependency (SA directive), only a learner holding a
 * {@code VERIFIED} registration may be recorded. This is a logical reference (raw {@code UUID}) —
 * there is deliberately no User module yet.</p>
 */
public record StudentId(UUID value) {

    public StudentId {
        if (value == null) {
            throw new IllegalArgumentException("StudentId must not be null.");
        }
    }

    public static StudentId of(UUID value) {
        return new StudentId(value);
    }
}