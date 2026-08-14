package io.github.ryu200o.eduworkshop.attendance.internal.domain.model;

/**
 * Lifecycle state of an attendance record, driven by {@code Workshop.state} (ADR 0019 §3):
 * <ul>
 *   <li>{@code OPEN} — workshop is {@code IN_PROGRESS}; the trainer may mark/correct attendance.</li>
 *   <li>{@code RECONCILING} — workshop is {@code COMPLETED} and the Reconciliation Window is open;
 *       students may appeal, auditors may adjust.</li>
 *   <li>{@code FINALIZED} — the Reconciliation Window has closed; the record is locked forever.</li>
 * </ul>
 */
public enum AttendanceState {
    OPEN,
    RECONCILING,
    FINALIZED
}