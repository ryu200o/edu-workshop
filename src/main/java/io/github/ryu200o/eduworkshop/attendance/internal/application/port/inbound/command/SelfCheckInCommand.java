package io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.Actor;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceState;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Student self QR check-in (Epic 3B) for a single learner while the workshop is {@code IN_PROGRESS}.
 *
 * <p><strong>Thin QR (Slice A):</strong> the {@code qrReference} is an <strong>opaque input</strong>
 * captured by the inbound adapter — the handler does not parse, resolve or validate its format
 * (OQ-3B-1/2 are deferred to Slice B). The handler only acts on the already-resolved
 * {@code workshopId} (from the adapter seam) and the {@code studentId} from the authenticated
 * principal ({@code actor}). A non-blank {@code qrReference} is required as an input invariant —
 * a QR check-in without a reference is malformed → {@link IllegalArgumentException} → HTTP 400.</p>
 *
 * <p>Global gates orchestrated by the handler (ADR 0005): the workshop must exist and be
 * {@code IN_PROGRESS} (OQ-3B-4, state authority ADR 0019 §3), the actor must be a {@code STUDENT},
 * the learner's registration must be {@code VERIFIED} (SA directive), and the result
 * {@code ATTENDED | LATE} is decided by the Workshop module ({@code evaluateCheckIn}, OQ-3B-5).
 * Repeated scans are an <strong>idempotent no-op</strong> (OQ-3B-3): the existing record is
 * returned unchanged — no new ledger entry, no publish, no {@code currentResult} change.</p>
 */
public record SelfCheckInCommand(
        UUID workshopId,
        String qrReference,
        Actor actor
) implements Command<SelfCheckInCommand.Result> {

    public SelfCheckInCommand {
        if (workshopId == null) {
            throw new IllegalArgumentException("workshopId must not be null.");
        }
        if (qrReference == null || qrReference.isBlank()) {
            throw new IllegalArgumentException("qrReference must not be blank.");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor must not be null.");
        }
    }

    public record Result(
            UUID recordId,
            AttendanceResult result,
            AttendanceState state
    ) {
    }
}
