package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.parameter;

/**
 * Create-time default for the Workshop-owned attendance late-policy (Epic 3C, OQ-3C-9).
 *
 * <p>The Workshop module owns the attendance policy (ADR 0019 §13.1) and computes
 * {@code ATTENDED | LATE} at the Application edge; the domain never references this type. Attendance
 * has no policy of its own. This config now serves <em>only</em> as the seed default persisted onto
 * each new workshop ({@code late_threshold_seconds}, seeded by {@code CreateWorkshopCommandHandler}):
 * the live evaluation reads the per-workshop persisted value at check-in time (OQ-3C-10), no longer
 * this global knob.</p>
 *
 * <p>Single knob {@code app.workshop.checkin.late-after-minutes} (default 15): a learner who checks
 * in no later than {@code startTime + lateAfterMinutes} is {@code ATTENDED}, otherwise {@code LATE}.</p>
 */
public record WorkshopCheckInParameters(
        int lateAfterMinutes
) {
}
