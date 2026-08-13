package io.github.ryu200o.eduworkshop.attendance.internal.domain.model;

/**
 * The kind of decision appended to the ledger (ADR 0019 §6). The ledger is append-only: once a
 * decision is recorded it is never updated or removed.
 */
public enum AttendanceAction {
    MARK,
    APPEAL,
    AUDITOR_ADJUST,
    FINALIZE
}