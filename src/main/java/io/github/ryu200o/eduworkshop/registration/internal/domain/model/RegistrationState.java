package io.github.ryu200o.eduworkshop.registration.internal.domain.model;

/**
 * Lifecycle state of a registration (ticket) for a workshop.
 *
 * <p>Per the SA+PO decision, Phase 1 has exactly two states: {@code REGISTERED} (the default when a
 * student books a seat) and {@code CANCELLED}. A single DB row per (workshop, user) pair flips
 * between the two — re-registering after a cancellation reactivates the same row rather than creating
 * a second one (see ADR 0012). Later phases may add {@code ATTENDED} / {@code NO_SHOW}.</p>
 *
 * <p>{@code REFUNDED} is the system-initiated outcome when a workshop is cancelled, and
 * {@code VERIFIED} marks a seat whose registration has been verified (SA directive — the Attendance
 * module only records attendance for {@code VERIFIED} seats). The {@code REGISTERED → VERIFIED}
 * transition, the verifier workflow and any API/UI/device integration are deliberately out of scope
 * for now (dedicated Registration Verification task); the state is added as an enum value so the
 * Attendance gate can consume it.</p>
 */
public enum RegistrationState {
    REGISTERED,
    CANCELLED,
    REFUNDED,
    VERIFIED
}
