package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.parameter;

/**
 * Operational Policy for QR self check-in (Epic 3B, OQ-3B-5) — a Workshop-side operational setting,
 * mirroring the {@code WorkshopBufferParameters} pattern (ADR 0018): the Workshop module owns the
 * attendance policy (ADR 0019 §13.1) and computes {@code ATTENDED | LATE} at the Application edge;
 * the domain never references this type. Attendance has no policy of its own.
 *
 * <p>Single knob {@code app.workshop.checkin.late-after-minutes} (default 15): a learner who checks
 * in no later than {@code startTime + lateAfterMinutes} is {@code ATTENDED}, otherwise {@code LATE}.
 * Per-workshop planner configuration is future work (backlog).</p>
 */
public record WorkshopCheckInParameters(
        int lateAfterMinutes
) {
}
