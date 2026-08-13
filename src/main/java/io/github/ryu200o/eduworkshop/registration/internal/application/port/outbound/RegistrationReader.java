package io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.MyRegistrationStatus;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.view.MyRegistrationView;

import java.util.List;
import java.util.UUID;

/**
 * Read-side outbound port (SPI) for the Registration read side. Consumer-Driven: it declares only
 * the lookups the query use cases / Module Facade actually need. Returns primitives / projections
 * directly (CQRS bypass — no domain aggregate reconstruction). Implementations must be side-effect
 * free.
 */
public interface RegistrationReader {

    /**
     * Counts the active ({@code REGISTERED}) seats taken for a workshop. This is the "anchor"
     * number Phase 2 uses to validate post-publish changes (cancelling the workshop, lowering the
     * capacity, changing the room).
     */
    int countActiveByWorkshop(UUID workshopId);

    /**
     * Counts the total active ({@code REGISTERED}) seats across multiple workshops.
     * Used by the Impact Preview query to determine how many students are affected
     * by a maintenance window.
     *
     * @param workshopIds the workshop ids to count registrations for
     * @return total number of active registrations across all specified workshops
     */
    int countActiveByWorkshopIds(List<UUID> workshopIds);

    /**
     * Lists a learner's bookings, optionally filtered by status. Fully backed by selectively
     * snapshotted columns on {@code registrations} (ADR 0007) — a single self-contained SELECT with
     * no cross-module JOIN. A {@code null} status returns the full history (including {@code
     * REFUNDED}); otherwise only rows matching the given status are returned (DB query pushdown, no
     * in-memory filtering).
     */
    List<MyRegistrationView> getByUserId(UUID userId, MyRegistrationStatus status);

    /**
     * Returns the status of the single (workshop, user) registration row, if it exists. Used by the
     * Registration Module Facade to expose the read-only {@code isVerified} predicate to the
     * Attendance module (SA directive) — Attendance never mutates Registration state, it only reads
     * it through this path.
     */
    java.util.Optional<MyRegistrationStatus> getStatusByWorkshopAndUser(UUID workshopId, UUID userId);
}
