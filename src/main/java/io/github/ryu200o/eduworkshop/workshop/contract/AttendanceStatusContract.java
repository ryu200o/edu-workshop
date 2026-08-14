package io.github.ryu200o.eduworkshop.workshop.contract;

/**
 * Consumer-driven contract (ADR 0010) for QR self check-in (Epic 3B): the Workshop module owns the
 * attendance policy (ADR 0019 §13.1) and decides, at the Application edge, whether a learner's
 * check-in counts as {@code ATTENDED} or {@code LATE} — the Attendance module only consumes this
 * result and never owns the policy.
 *
 * <p>No full Workshop model is exposed; {@code WorkshopExposeAPI#evaluateCheckIn} returns one of
 * these values, or {@code Optional.empty()} when the workshop does not exist.</p>
 */
public enum AttendanceStatusContract {
    ATTENDED,
    LATE
}
