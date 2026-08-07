package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

/**
 * Contract Terms value object: the reason a Planner renegotiates the Occupancy Contract after the
 * workshop is PUBLISHED (ADR 0018 P4) — e.g. when overriding buffer time. Replaces a raw
 * {@code reason} string with a self-validating VO. Invariant: non-blank.
 */
public record BufferJustification(String value) {

    public BufferJustification {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("BufferJustification must not be blank.");
        }
    }

    public static BufferJustification of(String raw) {
        return new BufferJustification(raw.trim());
    }
}
