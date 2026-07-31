package io.github.ryu200o.eduworkshop.registration.internal.domain.model;

import io.github.ryu200o.eduworkshop.registration.internal.domain.model.event.RegistrationCancelled;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.event.RegistrationCreated;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.event.RegistrationDomainEvent;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.event.RegistrationReactivated;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.exception.CancellationDeadlineExceededException;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.exception.InvalidRegistrationStateException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root representing a student's seat (ticket) for a workshop.
 *
 * <p><strong>Data model (ADR 0012):</strong> exactly one registration row exists per (workshop, user)
 * pair. Cancelling flips the row to {@code CANCELLED} and re-registering flips it back to
 * {@code REGISTERED} ({@link #reactivate}). This lets the Application layer rely on a plain unique
 * index on {@code (workshop_id, user_id)} as the race-proof backstop (a partial index on
 * {@code status='REGISTERED'} is not supported by the H2 test database), while still honouring the
 * business rule "at most one active seat per (workshop, user), but re-registration after a
 * cancellation is allowed".</p>
 *
 * <p><strong>Invariants enforced here (local, single-aggregate):</strong></p>
 * <ul>
 *   <li>{@code REGISTERED} is the only state a freshly created registration can be in.</li>
 *   <li>Cancellation is only allowed before the deadline — {@code now <= startTime − 24h} — otherwise
 *   {@link CancellationDeadlineExceededException} is raised.</li>
 *   <li>State transitions must follow the machine {@code REGISTERED → CANCELLED → REGISTERED}.</li>
 * </ul>
 *
 * <p>The <em>global</em> invariant "at most one active registration per (workshop, user)" is a
 * set-based rule and therefore orchestrated by the Application handler (ADR 0005), with the DB unique
 * index as the final backstop. The domain never receives a repository or policy parameter.</p>
 */
public class Registration {

    /** Business invariant: cancellation is only allowed no later than 24h before start. */
    public static final Duration CANCELLATION_DEADLINE = Duration.ofHours(24);

    private final RegistrationId id;
    private final StudentId studentId;
    private WorkshopReference workshopReference;
    private RegistrationState state;
    private Instant registeredAt;
    private Instant cancelledAt;
    private Instant updatedAt;

    private List<RegistrationDomainEvent> recordedEvents = new ArrayList<>();

    private Registration(RegistrationId id,
                         StudentId studentId,
                         WorkshopReference workshopReference,
                         RegistrationState state,
                         Instant registeredAt,
                         Instant cancelledAt,
                         Instant updatedAt) {
        this.id = requireNonNull(id, "RegistrationId cannot be null");
        this.studentId = requireNonNull(studentId, "StudentId cannot be null");
        this.workshopReference = requireNonNull(workshopReference, "workshopReference cannot be null");
        this.state = requireNonNull(state, "state cannot be null");
        this.registeredAt = requireNonNull(registeredAt, "registeredAt cannot be null");
        this.cancelledAt = cancelledAt;
        this.updatedAt = requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    /**
     * Books a seat for a student. The registration is born in state {@code REGISTERED}.
     */
    public static Registration create(RegistrationId id,
                                      StudentId studentId,
                                      WorkshopReference workshopReference,
                                      Instant now) {
        requireNonNull(id, "RegistrationId cannot be null");
        requireNonNull(studentId, "StudentId cannot be null");
        requireNonNull(workshopReference, "workshopReference cannot be null");
        requireNonNull(now, "now cannot be null");

        Registration registration = new Registration(id, studentId, workshopReference,
                RegistrationState.REGISTERED, now, null, now);
        registration.record(new RegistrationCreated(id, workshopReference.workshopId(), studentId,
                workshopReference.startTime(), now));
        return registration;
    }

    /**
     * Reconstitutes an existing registration from persistence. Bypasses all invariant checks (no
     * spurious re-validation on read), mirroring {@code Room.reconstruct}.
     */
    public static Registration reconstruct(RegistrationId id,
                                           StudentId studentId,
                                           WorkshopReference workshopReference,
                                           RegistrationState state,
                                           Instant registeredAt,
                                           Instant cancelledAt,
                                           Instant updatedAt) {
        return new Registration(id, studentId, workshopReference, state, registeredAt, cancelledAt, updatedAt);
    }

    /**
     * Cancels the seat (REGISTERED → CANCELLED), but only before the 24-hour deadline.
     *
     * @throws CancellationDeadlineExceededException if {@code now} is after {@code startTime − 24h}
     */
    public void cancel(Instant now) {
        requireNonNull(now, "now cannot be null");
        requireState(RegistrationState.REGISTERED, "cancel");

        Instant deadline = workshopReference.startTime().minus(CANCELLATION_DEADLINE);
        if (now.isAfter(deadline)) {
            throw new CancellationDeadlineExceededException(id, deadline, now);
        }

        this.state = RegistrationState.CANCELLED;
        this.cancelledAt = now;
        this.touch(now);

        record(new RegistrationCancelled(id, workshopReference.workshopId(), studentId, updatedAt));
    }

    /**
     * Re-activates a previously cancelled registration (CANCELLED → REGISTERED) on the same row.
     * Refreshes the {@link WorkshopReference} snapshot (e.g. an updated start time after a
     * reschedule) and resets the cancellation timestamp.
     */
    public void reactivate(WorkshopReference updatedReference, Instant now) {
        requireNonNull(updatedReference, "workshop reference must not be null");
        requireNonNull(now, "now cannot be null");
        requireState(RegistrationState.CANCELLED, "reactivate");

        this.workshopReference = updatedReference;
        this.state = RegistrationState.REGISTERED;
        this.registeredAt = now;
        this.cancelledAt = null;
        this.touch(now);

        record(new RegistrationReactivated(id, workshopReference.workshopId(), studentId,
                workshopReference.startTime(), updatedAt));
    }

    // ---------------------------------------------------------------------
    // Guards / helpers
    // ---------------------------------------------------------------------

    private void requireState(RegistrationState expected, String operation) {
        if (state != expected) {
            throw new InvalidRegistrationStateException(
                    id, state, expected,
                    "Cannot " + operation + " a registration in state " + state + "; expected " + expected + ".");
        }
    }

    private void touch(Instant now) {
        this.updatedAt = now;
    }

    private void record(RegistrationDomainEvent event) {
        recordedEvents.add(event);
    }

    private static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }

    // ---------------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------------

    public RegistrationId id() {
        return id;
    }

    public StudentId studentId() {
        return studentId;
    }

    public WorkshopReference workshopReference() {
        return workshopReference;
    }

    public RegistrationState state() {
        return state;
    }

    public Instant registeredAt() {
        return registeredAt;
    }

    public Instant cancelledAt() {
        return cancelledAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public List<RegistrationDomainEvent> recordedEvents() {
        return Collections.unmodifiableList(recordedEvents);
    }

    public void clearDomainEvents() {
        recordedEvents = new ArrayList<>();
    }
}
