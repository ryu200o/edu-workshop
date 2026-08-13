package io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorRole;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceAction;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;

import java.time.Instant;
import java.util.UUID;

/**
 * Read projection of a single Decision Ledger entry (ADR 0019 §6). Immutable; mirrors the persisted
 * ledger row so the audit trail is fully readable.
 */
public record AttendanceLedgerEntryView(
        int entryNumber,
        Instant timestamp,
        UUID actorId,
        ActorRole actorRole,
        AttendanceAction action,
        AttendanceResult result,
        String reason,
        String evidenceReference
) {
}