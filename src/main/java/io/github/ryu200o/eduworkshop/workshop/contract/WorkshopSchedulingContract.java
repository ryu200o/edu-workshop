package io.github.ryu200o.eduworkshop.workshop.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumer-driven contract (ADR 0017) exposing just the workshop identity + lifecycle state — plus,
 * when {@code COMPLETED}, the completion instant — that the Attendance module needs to decide whether
 * the workshop is {@code IN_PROGRESS} (trainer may mark attendance) and, in the recovery path, to
 * anchor the Reconciliation Window with the authoritative {@code completedAt} instead of the
 * consumer's {@code now} (ADR 0019 §4, OQ-4/OQ-10). No full Workshop model is exposed and no
 * cross-module JOIN is ever performed.
 *
 * @param workshopId  the workshop identity
 * @param state       the lifecycle state ({@link WorkshopStateContract})
 * @param completedAt the completion instant; non-null <em>only</em> when {@code state == COMPLETED}
 */
public record WorkshopSchedulingContract(
        UUID workshopId,
        WorkshopStateContract state,
        Instant completedAt
) {
}