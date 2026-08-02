package io.github.ryu200o.eduworkshop.registration.internal.domain;

import io.github.ryu200o.eduworkshop.registration.internal.domain.model.Registration;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationState;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.StudentId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.WorkshopReference;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.event.RegistrationCancelled;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.event.RegistrationCreated;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.event.RegistrationGracePeriodGranted;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.event.RegistrationReactivated;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.event.RegistrationRefunded;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.exception.CancellationDeadlineExceededException;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.exception.RegistrationDomainException;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    // Workshop starts 2026-09-01 09:00 UTC → cancellation deadline (start − 24h) = 2026-08-31 09:00 UTC.
    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant DEADLINE = START.minus(Registration.CANCELLATION_DEADLINE);
    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final StudentId STUDENT = StudentId.of(UUID.randomUUID());

    private WorkshopReference workshop() {
        return WorkshopReference.of(WORKSHOP_ID, START);
    }

    // ----------------------------------------------------------------
    // create
    // ----------------------------------------------------------------

    @Test
    void create_booksSeatInRegisteredStateWithCreatedEvent() {
        RegistrationId id = RegistrationId.generate();
        Registration registration = Registration.create(id, STUDENT, workshop(), NOW);

        assertThat(registration.id()).isEqualTo(id);
        assertThat(registration.studentId()).isEqualTo(STUDENT);
        assertThat(registration.workshopReference()).isEqualTo(workshop());
        assertThat(registration.state()).isEqualTo(RegistrationState.REGISTERED);
        assertThat(registration.registeredAt()).isEqualTo(NOW);
        assertThat(registration.createdAt()).isEqualTo(NOW);
        assertThat(registration.cancelledAt()).isNull();

        assertThat(registration.recordedEvents())
                .hasSize(1)
                .hasOnlyElementsOfType(RegistrationCreated.class);

        RegistrationCreated event = (RegistrationCreated) registration.recordedEvents().get(0);
        assertThat(event.registrationId()).isEqualTo(id);
        assertThat(event.workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(event.studentId()).isEqualTo(STUDENT);
        assertThat(event.startTime()).isEqualTo(START);
    }

    @Test
    void create_rejectsNullArguments() {
        assertThatThrownBy(() -> Registration.create(null, STUDENT, workshop(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Registration.create(RegistrationId.generate(), null, workshop(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Registration.create(RegistrationId.generate(), STUDENT, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Registration.create(RegistrationId.generate(), STUDENT, workshop(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------
    // cancel
    // ----------------------------------------------------------------

    @Test
    void cancel_beforeDeadline_cancelsSeatAndEmitsEvent() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);
        Instant cancelAt = DEADLINE.minusSeconds(1);

        registration.cancel(cancelAt);

        assertThat(registration.state()).isEqualTo(RegistrationState.CANCELLED);
        assertThat(registration.cancelledAt()).isEqualTo(cancelAt);

        assertThat(registration.recordedEvents())
                .hasSize(2)
                .hasExactlyElementsOfTypes(RegistrationCreated.class, RegistrationCancelled.class);

        RegistrationCancelled event = (RegistrationCancelled) registration.recordedEvents().get(1);
        assertThat(event.workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(event.studentId()).isEqualTo(STUDENT);
    }

    @Test
    void cancel_exactlyAtDeadline_isAllowed() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);

        registration.cancel(DEADLINE);

        assertThat(registration.state()).isEqualTo(RegistrationState.CANCELLED);
    }

    @Test
    void cancel_afterDeadline_isRejected() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);

        assertThatThrownBy(() -> registration.cancel(DEADLINE.plusSeconds(1)))
                .isInstanceOf(CancellationDeadlineExceededException.class)
                .satisfies(e -> {
                    CancellationDeadlineExceededException ex = (CancellationDeadlineExceededException) e;
                    assertThat(ex.getDeadline()).isEqualTo(DEADLINE);
                    assertThat(ex.getAttemptedAt()).isEqualTo(DEADLINE.plusSeconds(1));
                });
    }

    @Test
    void cancel_past24hDeadline_noGracePeriod_throwsException() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);
        // Past the 24h deadline and no grace window granted → rejected.
        Instant cancelAt = DEADLINE.plusSeconds(60);

        assertThatThrownBy(() -> registration.cancel(cancelAt))
                .isInstanceOf(CancellationDeadlineExceededException.class);
    }

    @Test
    void cancel_past24hDeadline_withinGracePeriod_success() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);
        // Reschedule happens at the instant just after the standard deadline, opening a 12h grace window.
        Instant rescheduledAt = DEADLINE.plusSeconds(60);
        registration.grantGracePeriod(rescheduledAt, START, rescheduledAt);
        // Cancel a bit later — still past the standard 24h deadline, but within the still-open grace window.
        Instant cancelAt = rescheduledAt.plusSeconds(60);
        assertThat(cancelAt).isBefore(START);

        registration.cancel(cancelAt);

        assertThat(registration.state()).isEqualTo(RegistrationState.CANCELLED);
    }

    @Test
    void cancel_past24hDeadline_gracePeriodExpired_throwsException() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);
        // Grace granted just after the deadline, then a much later cancel attempt is past the 12h expiry.
        Instant rescheduledAt = DEADLINE.plusSeconds(60);
        registration.grantGracePeriod(rescheduledAt, START, rescheduledAt);
        Instant cancelAt = rescheduledAt.plus(Registration.GRACE_PERIOD).plusSeconds(1);
        assertThat(cancelAt).isBefore(START);

        assertThatThrownBy(() -> registration.cancel(cancelAt))
                .isInstanceOf(CancellationDeadlineExceededException.class);
    }

    @Test
    void cancel_afterWorkshopStarted_evenWithinGracePeriod_throwsException() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);
        // Grace window wide open (extends past start), but the workshop has already started → always rejected.
        registration.grantGracePeriod(NOW, START, NOW);

        assertThatThrownBy(() -> registration.cancel(START.plusSeconds(1)))
                .isInstanceOf(CancellationDeadlineExceededException.class);
    }

    @Test
    void cancel_afterWorkshopStarted_isRejected() {
        // startTime already in the past ⇒ deadline long gone ⇒ cancellation impossible.
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);

        assertThatThrownBy(() -> registration.cancel(START.plusSeconds(60)))
                .isInstanceOf(CancellationDeadlineExceededException.class);
    }

    @Test
    void cancelOnWorkshopCancelled_flipsToCancelledRegardlessOfDeadline() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);
        // Past the 24h deadline — a student-initiated cancel() would fail, but a workshop
        // cancellation is system-initiated and must always succeed.
        Instant flipAt = DEADLINE.plusSeconds(60);

        registration.cancelOnWorkshopCancelled(flipAt);

        assertThat(registration.state()).isEqualTo(RegistrationState.CANCELLED);
        assertThat(registration.cancelledAt()).isEqualTo(flipAt);

        assertThat(registration.recordedEvents())
                .hasSize(2)
                .hasExactlyElementsOfTypes(RegistrationCreated.class, RegistrationCancelled.class);

        RegistrationCancelled event = (RegistrationCancelled) registration.recordedEvents().get(1);
        assertThat(event.workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(event.studentId()).isEqualTo(STUDENT);
    }

    @Test
    void cancelOnWorkshopCancelled_afterWorkshopStarted_stillSucceeds() {
        // A cancelled workshop has no seats left regardless of when the flip happens.
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);

        registration.cancelOnWorkshopCancelled(START.plusSeconds(60));

        assertThat(registration.state()).isEqualTo(RegistrationState.CANCELLED);
    }

    @Test
    void cancelOnWorkshopCancelled_throwsWhenNotRegistered() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);
        registration.cancelOnWorkshopCancelled(NOW);

        assertThatThrownBy(() -> registration.cancelOnWorkshopCancelled(NOW))
                .isInstanceOf(RegistrationDomainException.class);
    }

    @Test
    void cancelOnWorkshopCancelled_rejectsNullNow() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);

        assertThatThrownBy(() -> registration.cancelOnWorkshopCancelled(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------
    // refundBySystem
    // ----------------------------------------------------------------

    @Test
    void refundBySystem_REGISTERED_to_REFUNDED() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);

        registration.refundBySystem(NOW);

        assertThat(registration.state()).isEqualTo(RegistrationState.REFUNDED);
        assertThat(registration.cancelledAt()).isEqualTo(NOW);

        assertThat(registration.recordedEvents())
                .hasSize(2)
                .hasExactlyElementsOfTypes(RegistrationCreated.class, RegistrationRefunded.class);

        RegistrationRefunded event = (RegistrationRefunded) registration.recordedEvents().get(1);
        assertThat(event.workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(event.studentId()).isEqualTo(STUDENT);
    }

    @Test
    void refundBySystem_idempotent_whenAlreadyRefunded() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);
        registration.refundBySystem(NOW);
        registration.clearDomainEvents();

        registration.refundBySystem(NOW.plusSeconds(60));

        assertThat(registration.state()).isEqualTo(RegistrationState.REFUNDED);
        assertThat(registration.recordedEvents()).isEmpty();
    }

    @Test
    void refundBySystem_idempotent_whenAlreadyCancelled() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);
        registration.cancel(DEADLINE.minusSeconds(1));
        registration.clearDomainEvents();

        registration.refundBySystem(NOW);

        assertThat(registration.state()).isEqualTo(RegistrationState.CANCELLED);
        assertThat(registration.recordedEvents()).isEmpty();
    }

    @Test
    void refundBySystem_rejectsNullNow() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);

        assertThatThrownBy(() -> registration.refundBySystem(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancel_whenAlreadyCancelled_isRejected() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);
        registration.cancel(DEADLINE.minusSeconds(1));

        assertThatThrownBy(() -> registration.cancel(DEADLINE.minusSeconds(2)))
                .isInstanceOf(RegistrationDomainException.class);
    }

    @Test
    void cancel_rejectsNullNow() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);

        assertThatThrownBy(() -> registration.cancel(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------
    // reactivate
    // ----------------------------------------------------------------

    @Test
    void reactivate_afterCancellation_renewsSeatOnSameRow() {
        RegistrationId id = RegistrationId.generate();
        Registration registration = Registration.create(id, STUDENT, workshop(), NOW);
        registration.cancel(DEADLINE.minusSeconds(1));

        Instant renewAt = DEADLINE.minusSeconds(10);
        WorkshopReference updated = WorkshopReference.of(WORKSHOP_ID, START);
        registration.reactivate(updated, renewAt);

        assertThat(registration.state()).isEqualTo(RegistrationState.REGISTERED);
        assertThat(registration.registeredAt()).isEqualTo(renewAt);
        assertThat(registration.cancelledAt()).isNull();
        assertThat(registration.workshopReference()).isEqualTo(updated);

        assertThat(registration.recordedEvents())
                .hasSize(3)
                .hasExactlyElementsOfTypes(RegistrationCreated.class, RegistrationCancelled.class,
                        RegistrationReactivated.class);

        RegistrationReactivated event = (RegistrationReactivated) registration.recordedEvents().get(2);
        assertThat(event.registrationId()).isEqualTo(id);
        assertThat(event.startTime()).isEqualTo(START);
    }

    @Test
    void reactivate_whenNotCancelled_isRejected() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);

        assertThatThrownBy(() -> registration.reactivate(workshop(), NOW))
                .isInstanceOf(RegistrationDomainException.class);
    }

    @Test
    void reactivate_refreshesStartTimeSnapshot() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);
        registration.cancel(DEADLINE.minusSeconds(1));

        Instant newStart = START.plusSeconds(3600);
        registration.reactivate(WorkshopReference.of(WORKSHOP_ID, newStart), NOW);

        assertThat(registration.workshopReference().startTime()).isEqualTo(newStart);
    }

    // ----------------------------------------------------------------
    // grantGracePeriod
    // ----------------------------------------------------------------

    @Test
    void grantGracePeriod_setsGraceUntil12HoursAndUpdatesSnapshot() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);
        Instant rescheduledAt = NOW.plusSeconds(60);
        Instant newStart = START.plusSeconds(7200);

        registration.grantGracePeriod(rescheduledAt, newStart, NOW.plusSeconds(1));

        assertThat(registration.state()).isEqualTo(RegistrationState.REGISTERED);
        assertThat(registration.workshopReference().startTime()).isEqualTo(newStart);
        assertThat(registration.gracePeriodUntil()).isEqualTo(rescheduledAt.plus(Registration.GRACE_PERIOD));
        assertThat(registration.updatedAt()).isEqualTo(NOW.plusSeconds(1));

        assertThat(registration.recordedEvents())
                .hasSize(2)
                .hasExactlyElementsOfTypes(RegistrationCreated.class, RegistrationGracePeriodGranted.class);

        RegistrationGracePeriodGranted event = (RegistrationGracePeriodGranted) registration.recordedEvents().get(1);
        assertThat(event.registrationId()).isEqualTo(registration.id());
        assertThat(event.gracePeriodUntil()).isEqualTo(rescheduledAt.plus(Registration.GRACE_PERIOD));
    }

    @Test
    void grantGracePeriod_repeatedReschedule_extendsWindow() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);
        Instant firstReschedule = NOW.plusSeconds(1);
        registration.grantGracePeriod(firstReschedule, START, firstReschedule);
        registration.clearDomainEvents();

        Instant secondReschedule = firstReschedule.plusSeconds(100);
        registration.grantGracePeriod(secondReschedule, START.plusSeconds(3600), secondReschedule);

        assertThat(registration.gracePeriodUntil()).isEqualTo(secondReschedule.plus(Registration.GRACE_PERIOD));
        assertThat(registration.workshopReference().startTime()).isEqualTo(START.plusSeconds(3600));
    }

    @Test
    void grantGracePeriod_rejectsNull() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);

        assertThatThrownBy(() -> registration.grantGracePeriod(null, START, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registration.grantGracePeriod(NOW, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registration.grantGracePeriod(NOW, START, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------
    // reconstruct / misc
    // ----------------------------------------------------------------

    @Test
    void reconstruct_bypassesInvariants() {
        Registration registration = Registration.reconstruct(
                RegistrationId.generate(), STUDENT, workshop(),
                RegistrationState.CANCELLED, NOW, DEADLINE, NOW, DEADLINE, null);

        assertThat(registration.state()).isEqualTo(RegistrationState.CANCELLED);
        assertThat(registration.cancelledAt()).isEqualTo(DEADLINE);
        assertThat(registration.createdAt()).isEqualTo(NOW);
        assertThat(registration.gracePeriodUntil()).isNull();
        assertThat(registration.recordedEvents()).isEmpty();
    }

    @Test
    void recordedEvents_areClearable() {
        Registration registration = Registration.create(RegistrationId.generate(), STUDENT, workshop(), NOW);
        assertThat(registration.recordedEvents()).isNotEmpty();

        registration.clearDomainEvents();
        assertThat(registration.recordedEvents()).isEmpty();
    }
}
