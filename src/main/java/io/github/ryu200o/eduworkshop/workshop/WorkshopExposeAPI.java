package io.github.ryu200o.eduworkshop.workshop;

import io.github.ryu200o.eduworkshop.workshop.contract.AttendanceStatusContract;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopRegistrationContract;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopImpactContract;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopSchedulingContract;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public inter-module communication interface for the workshop module.
 * This is the only surface exposed to other modules.
 */
public interface WorkshopExposeAPI {

    /**
     * Acquires a pessimistic write lock on the workshop row (lock-anchor, ADR 0015) and returns the
     * registration snapshot. Used by the Registration module's capacity gate: all concurrent
     * registrations for the same workshop serialize on this single row-lock (the {@code workshops}
     * row always exists, unlike a possibly-empty {@code registrations} set), so the subsequent
     * {@code countActiveByWorkshop} read is stable and no seat is over-booked. Empty when the
     * workshop does not exist.
     */
    Optional<WorkshopRegistrationContract> lockForRegistration(UUID workshopId);

    /**
     * Returns the workshops assigned to a given room whose time window overlaps the specified range,
     * as consumer-driven DTOs (id + state). Empty when no workshop overlaps.
     *
     * @param roomId    the room to filter by
     * @param startTime the maintenance window start (inclusive lower bound)
     * @param endTime   the maintenance window end (null = indefinite)
     */
    List<WorkshopImpactContract> getByRoomAndTimeOverlap(UUID roomId, Instant startTime, Instant endTime);

    /**
     * Read-only (no lock) lookup of a workshop's identity + lifecycle state — plus the authoritative
     * {@code completedAt} when the workshop is {@code COMPLETED}. Used by the Attendance module:
     * (1) the mark-attendance gate requires {@code IN_PROGRESS}; (2) the recovery path anchors the
     * Reconciliation Window with {@code completedAt}, never with the consumer's {@code now}
     * (ADR 0019 §4, OQ-10). Empty when the workshop does not exist.
     */
    Optional<WorkshopSchedulingContract> getScheduling(UUID workshopId);

    /**
     * Evaluates a QR self check-in (Epic 3B/3C): decides, as the Attendance Policy Owner
     * (ADR 0019 §13.1), whether a learner's check-in at {@code checkedInAt} counts as
     * {@code ATTENDED} or {@code LATE}. Read-only (no lock), computed at the Application edge via
     * {@code WorkshopReader} against the workshop's {@code startTime} and its <em>persisted</em>
     * late-policy threshold {@code late_threshold_seconds} (Workshop-owned, Epic 3C OQ-3C-10 —
     * evaluated live at check-in time, no snapshot to Attendance). The Attendance module only
     * consumes the result — it never owns the policy. Empty when the workshop does not exist. The
     * {@code IN_PROGRESS} gate is <em>not</em> part of this evaluation: it is enforced by the
     * Attendance handler through {@link #getScheduling} (state authority, ADR 0019 §3).
     */
    Optional<AttendanceStatusContract> evaluateCheckIn(UUID workshopId, Instant checkedInAt);
}
