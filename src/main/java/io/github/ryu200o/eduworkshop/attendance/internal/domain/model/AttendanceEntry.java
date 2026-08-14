package io.github.ryu200o.eduworkshop.attendance.internal.domain.model;

import java.time.Instant;

/**
 * An immutable decision on the Attendance Decision Ledger (ADR 0019 §6). Entries are never updated
 * or removed — the aggregate only appends new entries, each with a strictly increasing
 * {@code entryNumber}.
 *
 * @param entryNumber        sequential position of the decision within the record (1-based)
 * @param timestamp          the moment the decision was made
 * @param actor              who performed the decision
 * @param action             the kind of decision (MARK / APPEAL / AUDITOR_ADJUST / FINALIZE)
 * @param result             the {@link AttendanceResult} state after this entry
 * @param reason             human-readable justification (mandatory for AUDITOR_ADJUST, optional otherwise)
 * @param evidenceReference  optional external evidence reference (URL, QR hash, camera log id, JSON ref)
 */
public record AttendanceEntry(
        int entryNumber,
        Instant timestamp,
        Actor actor,
        AttendanceAction action,
        AttendanceResult result,
        String reason,
        String evidenceReference
) {
}