package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

/**
 * Value object for the buffer time (in minutes) a workshop holds a room before and after the teaching
 * window. Local invariant: both values are non-negative. The upper bound is an Operational Policy
 * ({@code app.workshop.buffer.max-minutes}) configured at the Application layer — it is deliberately
 * NOT enforced here (ADR 0018 P2).
 */
public record WorkshopBuffer(int beforeMinutes, int afterMinutes) {

    public static final WorkshopBuffer ZERO = new WorkshopBuffer(0, 0);

    public WorkshopBuffer {
        if (beforeMinutes < 0) {
            throw new IllegalArgumentException("buffer beforeMinutes must be >= 0");
        }
        if (afterMinutes < 0) {
            throw new IllegalArgumentException("buffer afterMinutes must be >= 0");
        }
    }

    public static WorkshopBuffer of(int beforeMinutes, int afterMinutes) {
        return new WorkshopBuffer(beforeMinutes, afterMinutes);
    }
}
