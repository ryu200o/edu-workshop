package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

/**
 * Value object for a workshop's attendance late-policy threshold — the seconds a learner may check
 * in after {@code startTime} and still count as {@code ATTENDED} (beyond that = {@code LATE}).
 *
 * <p>Epic 3C, chốt OQ-3C-7: allowed range is <strong>0..86400 seconds (0–24 hours)</strong>. This
 * is the Workshop-owned persisted policy (ADR 0019 §13.1) — mutable while the workshop is
 * {@code DRAFT}/{@code PLANNED}/{@code PUBLISHED}, frozen from {@code IN_PROGRESS}. The DB CHECK
 * constraint {@code chk_workshops_late_threshold} is the storage ceiling; the VO is the Application
 * fast-fail (self-validating per ADR 0009).</p>
 */
public record WorkshopLateThreshold(int seconds) {

    public static final int MAX_SECONDS = 86400;

    public WorkshopLateThreshold {
        if (seconds < 0 || seconds > MAX_SECONDS) {
            throw new IllegalArgumentException(
                    "Workshop late threshold must be between 0 and %d seconds, but was %d.".formatted(MAX_SECONDS, seconds));
        }
    }

    public static WorkshopLateThreshold of(int seconds) {
        return new WorkshopLateThreshold(seconds);
    }
}