package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

/**
 * Contract Terms value object: the reason a Planner renegotiates an Occupancy Contract after the
 * workshop is PUBLISHED (ADR 0018 P4). Replaces a raw {@code reason} string with a self-validating VO.
 * Invariant: non-blank.
 */
public record AdjustmentJustification(String value) {

    public AdjustmentJustification {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AdjustmentJustification must not be blank.");
        }
    }

    public static AdjustmentJustification of(String raw) {
        return new AdjustmentJustification(raw.trim());
    }
}
