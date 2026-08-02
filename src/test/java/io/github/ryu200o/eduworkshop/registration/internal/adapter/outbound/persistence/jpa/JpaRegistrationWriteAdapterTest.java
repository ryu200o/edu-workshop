package io.github.ryu200o.eduworkshop.registration.internal.adapter.outbound.persistence.jpa;

import io.github.ryu200o.eduworkshop.registration.internal.application.exception.DuplicateRegistrationException;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.Registration;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationState;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.StudentId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.WorkshopReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class JpaRegistrationWriteAdapterTest {

    @Autowired
    private JpaRegistrationWriteAdapter adapter;

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");

    @Test
    void saveAndLoadById_roundTrip() {
        Registration registration = Registration.create(RegistrationId.generate(), StudentId.of(USER_ID),
                WorkshopReference.of(WORKSHOP_ID, START), Instant.parse("2026-08-01T10:00:00Z"));
        Instant graceUntil = Instant.parse("2026-08-02T10:00:00Z");
        Instant rescheduledAt = graceUntil.minus(Registration.GRACE_PERIOD);
        // Urgent reschedule (new start 6h after reschedule → 6h < 12h) so the grace window is actually granted.
        registration.grantGracePeriod(rescheduledAt,
                rescheduledAt.plus(java.time.Duration.ofHours(6)), graceUntil);

        Registration saved = adapter.save(registration);

        assertThat(saved.id()).isEqualTo(registration.id());
        assertThat(saved.state()).isEqualTo(RegistrationState.REGISTERED);

        Registration loaded = adapter.loadById(registration.id()).orElseThrow();
        assertThat(loaded.id()).isEqualTo(registration.id());
        assertThat(loaded.studentId()).isEqualTo(registration.studentId());
        assertThat(loaded.workshopReference()).isEqualTo(registration.workshopReference());
        assertThat(loaded.state()).isEqualTo(RegistrationState.REGISTERED);
        assertThat(loaded.registeredAt()).isNotNull();
        assertThat(loaded.createdAt()).isNotNull();
        assertThat(loaded.cancelledAt()).isNull();
        assertThat(loaded.gracePeriodUntil()).isEqualTo(graceUntil);
    }

    @Test
    void saveAndLoadById_gracePeriodNullByDefault() {
        Registration registration = Registration.create(RegistrationId.generate(), StudentId.of(USER_ID),
                WorkshopReference.of(WORKSHOP_ID, START), Instant.parse("2026-08-01T10:00:00Z"));

        adapter.save(registration);

        Registration loaded = adapter.loadById(registration.id()).orElseThrow();
        assertThat(loaded.gracePeriodUntil()).isNull();
    }

    @Test
    void loadById_absent_returnsEmpty() {
        assertThat(adapter.loadById(RegistrationId.generate())).isEmpty();
    }

    @Test
    void loadByWorkshopAndUser_findsTheSingleRow() {
        Registration registration = Registration.create(RegistrationId.generate(), StudentId.of(USER_ID),
                WorkshopReference.of(WORKSHOP_ID, START), Instant.parse("2026-08-01T10:00:00Z"));
        adapter.save(registration);

        assertThat(adapter.loadByWorkshopAndUser(WORKSHOP_ID, USER_ID))
                .isPresent()
                .get()
                .extracting(Registration::id)
                .isEqualTo(registration.id());

        assertThat(adapter.loadByWorkshopAndUser(WORKSHOP_ID, UUID.randomUUID())).isEmpty();
    }

    @Test
    void loadAllByWorkshopIdAndState_returnsFilteredRows() {
        UUID workshopA = UUID.randomUUID();
        UUID workshopB = UUID.randomUUID();

        adapter.save(Registration.create(RegistrationId.generate(), StudentId.of(UUID.randomUUID()),
                WorkshopReference.of(workshopA, START), Instant.parse("2026-08-01T10:00:00Z")));
        adapter.save(Registration.create(RegistrationId.generate(), StudentId.of(UUID.randomUUID()),
                WorkshopReference.of(workshopA, START), Instant.parse("2026-08-01T10:00:00Z")));
        Registration cancelled = Registration.create(RegistrationId.generate(), StudentId.of(UUID.randomUUID()),
                WorkshopReference.of(workshopA, START), Instant.parse("2026-08-01T10:00:00Z"));
        cancelled.cancel(Instant.parse("2026-08-31T08:00:00Z"));
        adapter.save(cancelled);
        adapter.save(Registration.create(RegistrationId.generate(), StudentId.of(UUID.randomUUID()),
                WorkshopReference.of(workshopB, START), Instant.parse("2026-08-01T10:00:00Z")));

        assertThat(adapter.loadAllByWorkshopIdAndState(workshopA, RegistrationState.REGISTERED)).hasSize(2);
        assertThat(adapter.loadAllByWorkshopIdAndState(workshopA, RegistrationState.CANCELLED)).hasSize(1);
        assertThat(adapter.loadAllByWorkshopIdAndState(workshopB, RegistrationState.REGISTERED)).hasSize(1);
        assertThat(adapter.loadAllByWorkshopIdAndState(workshopB, RegistrationState.CANCELLED)).isEmpty();
        assertThat(adapter.loadAllByWorkshopIdAndState(UUID.randomUUID(), RegistrationState.REGISTERED)).isEmpty();
    }

    @Test
    void saveAll_persistsAllRegistrationsInOneBatch() {
        UUID workshop = UUID.randomUUID();

        List<Registration> registrations = List.of(
                Registration.create(RegistrationId.generate(), StudentId.of(UUID.randomUUID()),
                        WorkshopReference.of(workshop, START), Instant.parse("2026-08-01T10:00:00Z")),
                Registration.create(RegistrationId.generate(), StudentId.of(UUID.randomUUID()),
                        WorkshopReference.of(workshop, START), Instant.parse("2026-08-01T10:00:00Z"))
        );

        adapter.saveAll(registrations);

        assertThat(adapter.loadAllByWorkshopIdAndState(workshop, RegistrationState.REGISTERED)).hasSize(2);
    }

    @Test
    void duplicateInsert_isTranslatedToDuplicateRegistrationException() {
        Registration first = Registration.create(RegistrationId.generate(), StudentId.of(USER_ID),
                WorkshopReference.of(WORKSHOP_ID, START), Instant.parse("2026-08-01T10:00:00Z"));
        Registration second = Registration.create(RegistrationId.generate(), StudentId.of(USER_ID),
                WorkshopReference.of(WORKSHOP_ID, START), Instant.parse("2026-08-01T11:00:00Z"));

        adapter.save(first);

        // The unique index uk_registrations_workshop_user is the race-proof backstop: a concurrent
        // duplicate insert is translated into the business exception.
        assertThatThrownBy(() -> adapter.save(second))
                .isInstanceOf(DuplicateRegistrationException.class);
    }
}
