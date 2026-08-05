package io.github.ryu200o.eduworkshop.registration.internal.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Decoupled reference to the workshop a registration belongs to.
 *
 * <p>Carries the logical {@code workshopId} plus a <em>selective snapshot</em> of the workshop's
 * display/timing data (tinh thần ADR 0007): {@code startTime}, {@code endTime}, {@code title} and
 * {@code roomName}. The start-time snapshot lets the Registration bounded context enforce its own
 * cancellation-deadline invariant autonomously — no temporal coupling to the Workshop module at
 * cancellation time. The display snapshots keep the learner "My Bookings" read side a single
 * self-contained SELECT on the {@code registrations} table (no cross-module JOIN). Snapshots are
 * refreshed when the workshop is rescheduled (Registration listens to
 * {@code WorkshopRescheduled} via the outbox).</p>
 *
 * <p>{@code title}, {@code endTime} and {@code roomName} are nullable: historical rows created
 * before V15 keep NULL snapshots (the read view falls back to empty strings), while newly created
 * / reactivated seats always carry them. {@code startTime} remains mandatory.</p>
 *
 * <p>This is the Registration module's own VO; it never imports Workshop {@code internal} types
 * (per ADR 0010). The Application layer maps a {@code WorkshopExposeAPI} contract DTO into this VO.</p>
 */
public record WorkshopReference(UUID workshopId, Instant startTime, String title, Instant endTime, String roomName) {

    public WorkshopReference {
        if (workshopId == null) {
            throw new IllegalArgumentException("workshopId must not be null.");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("startTime must not be null.");
        }
    }

    public static WorkshopReference of(UUID workshopId, Instant startTime) {
        return new WorkshopReference(workshopId, startTime, null, null, null);
    }

    public static WorkshopReference of(UUID workshopId, Instant startTime, String title, Instant endTime, String roomName) {
        return new WorkshopReference(workshopId, startTime, title, endTime, roomName);
    }

    /**
     * Returns a copy of this reference with refreshed scheduling snapshots. Used when a workshop is
     * rescheduled: {@code startTime} and {@code endTime} are updated while the display snapshots
     * ({@code title}, {@code roomName}) — which a reschedule does not change — are preserved.
     */
    public WorkshopReference withSchedule(Instant newStartTime, Instant newEndTime) {
        return new WorkshopReference(workshopId, newStartTime, title, newEndTime, roomName);
    }
}
