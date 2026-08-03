package io.github.ryu200o.eduworkshop.room.internal.domain;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCapacity;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomState;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCreated;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCapacityChanged;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomMaintenanceScheduled;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRenamedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRelocatedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomStateChanged;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.IllegalRoomStateException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.InvalidMaintenanceScheduleException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.MaintenanceId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.MaintenanceSchedule;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RoomTest {

    private static final Instant NOW = Instant.now();
    private static final RoomLocation LOCATION = RoomLocation.of("F", 2);
    private static final String NAME = "F.0201";
    private static final int CODE = 1;
    private static final int CAPACITY = 50;

    private static RoomName name() {
        return RoomName.of(NAME);
    }

    private static Room newRoom() {
        return Room.create(RoomId.generate(), name(), LOCATION, RoomCode.of(CODE), RoomCapacity.of(CAPACITY), NOW);
    }

    @Test
    void create_yieldsActiveRoomAndEmitsRoomCreated() {
        Instant now = Instant.now();
        Room room = Room.create(RoomId.generate(), name(), LOCATION, RoomCode.of(CODE), RoomCapacity.of(CAPACITY), now);

        assertThat(room.id()).isNotNull();
        assertThat(room.name()).isEqualTo(name());
        assertThat(room.code()).isEqualTo(RoomCode.of(CODE));
        assertThat(room.capacity()).isEqualTo(RoomCapacity.of(CAPACITY));
        assertThat(room.location()).isEqualTo(LOCATION);
        assertThat(room.state()).isEqualTo(RoomState.ACTIVE);
        assertThat(room.createdAt()).isNotNull();
        assertThat(room.updatedAt()).isEqualTo(room.createdAt());

        assertThat(room.recordedEvents()).hasSize(1);
        assertThat(room.recordedEvents().get(0))
                .isInstanceOf(RoomCreated.class)
                .satisfies(e -> {
                    RoomCreated created = (RoomCreated) e;
                    assertThat(created.roomId()).isEqualTo(room.id());
                    assertThat(created.name()).isEqualTo(name());
                    assertThat(created.code()).isEqualTo(RoomCode.of(CODE));
                    assertThat(created.capacity()).isEqualTo(RoomCapacity.of(CAPACITY));
                    assertThat(created.location()).isEqualTo(LOCATION);
                    assertThat(created.initialState()).isEqualTo(RoomState.ACTIVE);
                });
    }

    @Test
    void create_withExplicitIdentity_emitsCreatedEventAndPreservesTimestamps() {
        RoomId roomId = RoomId.of(UUID.randomUUID());
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

        Room room = Room.create(roomId, name(), LOCATION, RoomCode.of(CODE), RoomCapacity.of(CAPACITY), createdAt);

        assertThat(room.id()).isEqualTo(roomId);
        assertThat(room.createdAt()).isEqualTo(createdAt);
        assertThat(room.updatedAt()).isEqualTo(createdAt);
        assertThat(room.recordedEvents()).hasSize(1);
        assertThat(room.recordedEvents().get(0)).isInstanceOf(RoomCreated.class);
    }

    @Test
    void reconstruct_preservesPersistedStateAndTimestamps_withoutEvents() {
        RoomId roomId = RoomId.of(UUID.randomUUID());
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-06-01T00:00:00Z");

        Room room = Room.reconstruct(roomId, name(), LOCATION, RoomCode.of(CODE), RoomCapacity.of(CAPACITY),
                RoomState.MAINTENANCE, createdAt, updatedAt);

        assertThat(room.id()).isEqualTo(roomId);
        assertThat(room.state()).isEqualTo(RoomState.MAINTENANCE);
        assertThat(room.createdAt()).isEqualTo(createdAt);
        assertThat(room.updatedAt()).isEqualTo(updatedAt);
        assertThat(room.recordedEvents()).isEmpty();
    }

    @Test
    void reconstruct_withNullState_isRejected() {
        RoomId roomId = RoomId.of(UUID.randomUUID());
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        assertThatThrownBy(() -> Room.reconstruct(roomId, name(), LOCATION, RoomCode.of(CODE),
                RoomCapacity.of(CAPACITY), null, now, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_rejectsNullName() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> Room.create(RoomId.generate(), null, LOCATION, RoomCode.of(CODE),
                RoomCapacity.of(CAPACITY), now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_rejectsNonPositiveCode() {
        assertThatThrownBy(() -> RoomCode.of(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RoomCode.of(-5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> RoomCapacity.of(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RoomCapacity.of(-5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changeCode_rejectsNonPositiveCode() {
        assertThatThrownBy(() -> RoomCode.of(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RoomCode.of(-3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changeCode_sameCode_isIdempotent_noEvent() {
        Room room = newRoom();
        int before = room.recordedEvents().size();

        room.changeCode(RoomCode.of(CODE), NOW);

        assertThat(room.code()).isEqualTo(RoomCode.of(CODE));
        assertThat(room.recordedEvents()).hasSize(before);
    }

    @Test
    void changeCode_fromDeactivated_isRejected() {
        Room room = newRoom();
        room.deactivate(NOW);

        assertThatThrownBy(() -> room.changeCode(RoomCode.of(99), NOW))
                .isInstanceOf(IllegalRoomStateException.class);
        assertThat(room.code()).isEqualTo(RoomCode.of(CODE));
    }

    @Test
    void changeName_sameName_isIdempotent_noEvent() {
        Room room = newRoom();
        int before = room.recordedEvents().size();

        room.changeName(RoomName.of(NAME), NOW);

        assertThat(room.name()).isEqualTo(name());
        assertThat(room.recordedEvents()).hasSize(before);
    }

    @Test
    void changeName_fromDeactivated_isRejected() {
        Room room = newRoom();
        room.deactivate(NOW);

        assertThatThrownBy(() -> room.changeName(RoomName.of("LAB-101"), NOW))
                .isInstanceOf(IllegalRoomStateException.class);
        assertThat(room.name()).isEqualTo(name());
    }

    @Test
    void relocateTo_sameLocation_isIdempotent_noEvent() {
        Room room = newRoom();
        int before = room.recordedEvents().size();

        room.relocateTo(LOCATION, NOW);

        assertThat(room.location()).isEqualTo(LOCATION);
        assertThat(room.recordedEvents()).hasSize(before);
    }

    @Test
    void placeUnderMaintenance_fromActive_transitionsToMaintenanceAndEmitsEvent() {
        Room room = newRoom();

        room.placeUnderMaintenance(NOW);

        assertThat(room.state()).isEqualTo(RoomState.MAINTENANCE);
        assertThat(room.updatedAt()).isAfterOrEqualTo(room.createdAt());
        assertThat(room.recordedEvents())
                .filteredOn(RoomStateChanged.class::isInstance)
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    RoomStateChanged changed = (RoomStateChanged) e;
                    assertThat(changed.previousState()).isEqualTo(RoomState.ACTIVE);
                    assertThat(changed.newState()).isEqualTo(RoomState.MAINTENANCE);
                    assertThat(changed.roomId()).isEqualTo(room.id());
                });
    }

    @Test
    void placeUnderMaintenance_fromMaintenance_isIdempotentAndEmitsNoEvent() {
        Room room = newRoom();
        room.placeUnderMaintenance(NOW);
        int eventsBefore = room.recordedEvents().size();

        room.placeUnderMaintenance(NOW);

        assertThat(room.state()).isEqualTo(RoomState.MAINTENANCE);
        assertThat(room.recordedEvents()).hasSize(eventsBefore);
    }

    @Test
    void placeUnderMaintenance_fromDeactivated_isRejected() {
        Room room = newRoom();
        room.deactivate(NOW);

        IllegalRoomStateException ex = catchThrowableOfType(
                () -> room.placeUnderMaintenance(NOW), IllegalRoomStateException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCurrentState()).isEqualTo(RoomState.DEACTIVATED);
        assertThat(ex.getAttemptedState()).isEqualTo(RoomState.MAINTENANCE);
        assertThat(room.state()).isEqualTo(RoomState.DEACTIVATED);
    }

    @Test
    void reactivate_fromMaintenance_returnsToActive() {
        Room room = newRoom();
        room.placeUnderMaintenance(NOW);

        room.reactivate(NOW);

        assertThat(room.state()).isEqualTo(RoomState.ACTIVE);
        assertThat(room.recordedEvents())
                .filteredOn(RoomStateChanged.class::isInstance)
                .hasSize(2);
    }

    @Test
    void reactivate_fromActive_isIdempotentAndEmitsNoEvent() {
        Room room = newRoom();
        int eventsBefore = room.recordedEvents().size();

        room.reactivate(NOW);

        assertThat(room.state()).isEqualTo(RoomState.ACTIVE);
        assertThat(room.recordedEvents()).hasSize(eventsBefore);
    }

    @Test
    void reactivate_fromDeactivated_isRejected() {
        Room room = newRoom();
        room.deactivate(NOW);

        assertThatThrownBy(() -> room.reactivate(NOW))
                .isInstanceOf(IllegalRoomStateException.class);

        assertThat(room.state()).isEqualTo(RoomState.DEACTIVATED);
    }

    @Test
    void deactivate_fromActive_permanentlyFreezesRoom() {
        Room room = newRoom();

        room.deactivate(NOW);

        assertThat(room.state()).isEqualTo(RoomState.DEACTIVATED);
        assertThat(room.recordedEvents())
                .filteredOn(RoomStateChanged.class::isInstance)
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    RoomStateChanged changed = (RoomStateChanged) e;
                    assertThat(changed.previousState()).isEqualTo(RoomState.ACTIVE);
                    assertThat(changed.newState()).isEqualTo(RoomState.DEACTIVATED);
                });
    }

    @Test
    void deactivate_fromMaintenance_permanentlyFreezesRoom() {
        Room room = newRoom();
        room.placeUnderMaintenance(NOW);

        room.deactivate(NOW);

        assertThat(room.state()).isEqualTo(RoomState.DEACTIVATED);
    }

    @Test
    void deactivate_fromDeactivated_isIdempotentNoOp() {
        Room room = newRoom();
        room.deactivate(NOW);
        int eventsBefore = room.recordedEvents().size();

        room.deactivate(NOW);

        assertThat(room.state()).isEqualTo(RoomState.DEACTIVATED);
        assertThat(room.recordedEvents()).hasSize(eventsBefore);
    }

    @Test
    void deactivatedRoom_blocksReactivationAndMaintenance_butDeactivateIsSafeNoOp() {
        Room room = newRoom();
        room.deactivate(NOW);

        assertThatThrownBy(() -> room.placeUnderMaintenance(NOW)).isInstanceOf(IllegalRoomStateException.class);
        assertThatThrownBy(() -> room.reactivate(NOW)).isInstanceOf(IllegalRoomStateException.class);
        assertThatCode(() -> room.deactivate(NOW)).doesNotThrowAnyException();
        assertThat(room.state()).isEqualTo(RoomState.DEACTIVATED);
    }

    @Test
    void changeCode_changesCodeSilently_noEvent() {
        Room room = newRoom();

        room.changeCode(RoomCode.of(99), NOW);

        assertThat(room.code()).isEqualTo(RoomCode.of(99));
        assertThat(room.name()).isEqualTo(name());
        assertThat(room.location()).isEqualTo(LOCATION);
        assertThat(room.updatedAt()).isAfterOrEqualTo(room.createdAt());
        assertThat(room.recordedEvents())
                .filteredOn(RoomRenamedEvent.class::isInstance)
                .isEmpty();
    }

    @Test
    void changeName_recomputesNothingButEmitsRoomRenamedEvent() {
        Room room = newRoom();

        room.changeName(RoomName.of("LAB-101"), NOW);

        assertThat(room.name()).isEqualTo(RoomName.of("LAB-101"));
        assertThat(room.location()).isEqualTo(LOCATION);
        assertThat(room.code()).isEqualTo(RoomCode.of(CODE));
        assertThat(room.updatedAt()).isAfterOrEqualTo(room.createdAt());
        assertThat(room.recordedEvents())
                .filteredOn(RoomRenamedEvent.class::isInstance)
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    RoomRenamedEvent ev = (RoomRenamedEvent) e;
                    assertThat(ev.roomId()).isEqualTo(room.id());
                    assertThat(ev.oldName()).isEqualTo(name());
                    assertThat(ev.newName()).isEqualTo(RoomName.of("LAB-101"));
                });
    }

    @Test
    void clearDomainEvents_removesRecordedEvents() {
        Room room = newRoom();
        room.deactivate(NOW);

        room.clearDomainEvents();

        assertThat(room.recordedEvents()).isEmpty();
    }

    @Test
    void relocateTo_keepsNameAndCodeAndEmitsLocationChanged() {
        Room room = newRoom();
        RoomLocation newLocation = RoomLocation.of("G", 3);

        room.relocateTo(newLocation, NOW);

        assertThat(room.location()).isEqualTo(newLocation);
        assertThat(room.name()).isEqualTo(name());
        assertThat(room.code()).isEqualTo(RoomCode.of(CODE));
        assertThat(room.updatedAt()).isAfterOrEqualTo(room.createdAt());
        assertThat(room.recordedEvents())
                .filteredOn(RoomRelocatedEvent.class::isInstance)
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    RoomRelocatedEvent ev = (RoomRelocatedEvent) e;
                    assertThat(ev.roomId()).isEqualTo(room.id());
                    assertThat(ev.oldLocation()).isEqualTo(LOCATION);
                    assertThat(ev.newLocation()).isEqualTo(newLocation);
                });
    }

    @Test
    void relocateTo_preservesNameAndCode() {
        Room room = newRoom();

        room.relocateTo(RoomLocation.of("G", 3), NOW);

        assertThat(room.name()).isEqualTo(name());
        assertThat(room.code()).isEqualTo(RoomCode.of(CODE));
    }

    @Test
    void relocateTo_rejectsInvalidLocation() {
        Room room = newRoom();

        assertThatThrownBy(() -> room.relocateTo(RoomLocation.of("G", 0), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void relocateTo_fromDeactivated_isRejected() {
        Room room = newRoom();
        room.deactivate(NOW);

        assertThatThrownBy(() -> room.relocateTo(RoomLocation.of("G", 3), NOW))
                .isInstanceOf(IllegalRoomStateException.class);
        assertThat(room.location()).isEqualTo(LOCATION);
    }

    @Test
    void changeCapacity_updatesCapacityAndEmitsRoomCapacityChanged() {
        Room room = newRoom();

        room.changeCapacity(RoomCapacity.of(80), NOW);

        assertThat(room.capacity()).isEqualTo(RoomCapacity.of(80));
        assertThat(room.updatedAt()).isAfterOrEqualTo(room.createdAt());
        assertThat(room.recordedEvents())
                .filteredOn(RoomCapacityChanged.class::isInstance)
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    RoomCapacityChanged ev = (RoomCapacityChanged) e;
                    assertThat(ev.roomId()).isEqualTo(room.id());
                    assertThat(ev.oldCapacity()).isEqualTo(RoomCapacity.of(CAPACITY));
                    assertThat(ev.newCapacity()).isEqualTo(RoomCapacity.of(80));
                    assertThat(ev.occurredAt()).isEqualTo(room.updatedAt());
                });
    }

    @Test
    void changeCapacity_rejectsNonPositive() {
        assertThatThrownBy(() -> RoomCapacity.of(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RoomCapacity.of(-5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changeCapacity_sameCapacity_isIdempotentNoEvent() {
        Room room = newRoom();
        int before = room.recordedEvents().size();

        room.changeCapacity(RoomCapacity.of(CAPACITY), NOW);

        assertThat(room.capacity()).isEqualTo(RoomCapacity.of(CAPACITY));
        assertThat(room.recordedEvents()).hasSize(before);
    }

    @Test
    void changeCapacity_fromDeactivated_isRejected() {
        Room room = newRoom();
        room.deactivate(NOW);

        assertThatThrownBy(() -> room.changeCapacity(RoomCapacity.of(80), NOW))
                .isInstanceOf(IllegalRoomStateException.class);
        assertThat(room.capacity()).isEqualTo(RoomCapacity.of(CAPACITY));
    }

    @Test
    void scheduleMaintenance_validInput_createsScheduleAndEmitsEvent() {
        Room room = newRoom();
        Instant start = NOW.plusSeconds(3600);
        Instant end = NOW.plusSeconds(7200);

        MaintenanceSchedule schedule = room.scheduleMaintenance(
                MaintenanceId.generate(), start, end,
                "Quarterly HVAC filter replacement and duct cleaning", "operator-1", NOW);

        assertThat(schedule).isNotNull();
        assertThat(schedule.roomId()).isEqualTo(room.id());
        assertThat(schedule.startTime()).isEqualTo(start);
        assertThat(schedule.endTime()).isEqualTo(end);
        assertThat(room.recordedEvents())
                .filteredOn(RoomMaintenanceScheduled.class::isInstance)
                .hasSize(1);
    }

    @Test
    void scheduleMaintenance_deactivatedRoom_throws() {
        Room room = newRoom();
        room.deactivate(NOW);

        assertThatThrownBy(() -> room.scheduleMaintenance(
                MaintenanceId.generate(),
                NOW.plusSeconds(3600),
                NOW.plusSeconds(7200),
                "Quarterly HVAC filter replacement and duct cleaning",
                "operator-1", NOW))
                .isInstanceOf(IllegalRoomStateException.class);
    }

    @Test
    void scheduleMaintenance_reasonTooShort_throws() {
        Room room = newRoom();

        assertThatThrownBy(() -> room.scheduleMaintenance(
                MaintenanceId.generate(),
                NOW.plusSeconds(3600),
                NOW.plusSeconds(7200),
                "short",
                "operator-1", NOW))
                .isInstanceOf(InvalidMaintenanceScheduleException.class);
    }
}
