package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;

/**
 * Domain event emitted when a workshop is scheduled (DRAFT → SCHEDULED).
 *
 * <p>Carries the <em>new</em> planning values only. On the first (and, in this slice, only) schedule the
 * prior room/time/capacity are all null, so a redundant "old" block would be meaningless — hence the
 * single-sided payload. A future {@code reschedule()} transition (out of scope here) would carry a true
 * before/after diff instead.</p>
 */
public record WorkshopScheduled(
        WorkshopId workshopId,
        RoomReference roomReference,
        Instant startTime,
        Instant endTime,
        WorkshopCapacity capacity,
        Instant occurredAt
) implements WorkshopDomainEvent {
}
