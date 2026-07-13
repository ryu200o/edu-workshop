package io.github.ryu200o.eduworkshop.room.internal.domain;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.entity.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCreated;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCapacityChanged;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRenamedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRenameReason;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomStateChanged;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.IllegalRoomStateException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.state.RoomState;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RoomTest {

    private static final RoomLocation LOCATION = RoomLocation.of("F", 2);
    private static final String CODE = "01";
    private static final int CAPACITY = 50;

    private static RoomName name() {
        return RoomName.of(LOCATION, CODE);
    }

    @Test
    void create_yieldsActiveRoomAndEmitsRoomCreated() {
        Room room = Room.create(name(), LOCATION, CAPACITY);

        assertThat(room.id()).isNotNull();
        assertThat(room.name()).isEqualTo(name());
        assertThat(room.capacity()).isEqualTo(CAPACITY);
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
                    assertThat(created.location()).isEqualTo(LOCATION);
                    assertThat(created.initialState()).isEqualTo(RoomState.ACTIVE);
                });
    }

    @Test
    void create_withExplicitIdentity_emitsCreatedEventAndPreservesTimestamps() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-01-01T00:00:00Z");

        Room room = Room.create(id, name(), LOCATION, CAPACITY, createdAt, updatedAt);

        assertThat(room.id()).isEqualTo(id);
        assertThat(room.createdAt()).isEqualTo(createdAt);
        assertThat(room.updatedAt()).isEqualTo(updatedAt);
        assertThat(room.recordedEvents()).hasSize(1);
        assertThat(room.recordedEvents().get(0)).isInstanceOf(RoomCreated.class);
    }

    @Test
    void reconstruct_preservesPersistedStateAndTimestamps_withoutEvents() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-06-01T00:00:00Z");

        Room room = Room.reconstruct(id, name(), LOCATION, CAPACITY, RoomState.MAINTENANCE, createdAt, updatedAt);

        assertThat(room.id()).isEqualTo(id);
        assertThat(room.state()).isEqualTo(RoomState.MAINTENANCE);
        assertThat(room.createdAt()).isEqualTo(createdAt);
        assertThat(room.updatedAt()).isEqualTo(updatedAt);
        assertThat(room.recordedEvents()).isEmpty();
    }

    @Test
    void reconstruct_withNullState_isRejected() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        assertThatThrownBy(() -> Room.reconstruct(id, name(), LOCATION, CAPACITY, null, now, now))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void create_rejectsNullName() {
        assertThatThrownBy(() -> Room.create(null, LOCATION, CAPACITY))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void create_rejectsNameInconsistentWithLocation() {
        RoomName foreign = RoomName.ofRaw("G.0301");

        assertThatThrownBy(() -> Room.create(foreign, LOCATION, CAPACITY))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void create_rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> Room.create(name(), LOCATION, 0))
                .isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> Room.create(name(), LOCATION, -5))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void placeUnderMaintenance_fromActive_transitionsToMaintenanceAndEmitsEvent() {
        Room room = Room.create(name(), LOCATION, CAPACITY);

        room.placeUnderMaintenance();

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
        Room room = Room.create(name(), LOCATION, CAPACITY);
        room.placeUnderMaintenance();
        int eventsBefore = room.recordedEvents().size();

        room.placeUnderMaintenance();

        assertThat(room.state()).isEqualTo(RoomState.MAINTENANCE);
        assertThat(room.recordedEvents()).hasSize(eventsBefore);
    }

    @Test
    void placeUnderMaintenance_fromDeactivated_isRejected() {
        Room room = Room.create(name(), LOCATION, CAPACITY);
        room.deactivate();

        IllegalRoomStateException ex = catchThrowableOfType(
                () -> room.placeUnderMaintenance(), IllegalRoomStateException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCurrentState()).isEqualTo(RoomState.DEACTIVATED);
        assertThat(ex.getAttemptedState()).isEqualTo(RoomState.MAINTENANCE);
        assertThat(room.state()).isEqualTo(RoomState.DEACTIVATED);
    }

    @Test
    void reactivate_fromMaintenance_returnsToActive() {
        Room room = Room.create(name(), LOCATION, CAPACITY);
        room.placeUnderMaintenance();

        room.reactivate();

        assertThat(room.state()).isEqualTo(RoomState.ACTIVE);
        assertThat(room.recordedEvents())
                .filteredOn(RoomStateChanged.class::isInstance)
                .hasSize(2);
    }

    @Test
    void reactivate_fromActive_isIdempotentAndEmitsNoEvent() {
        Room room = Room.create(name(), LOCATION, CAPACITY);
        int eventsBefore = room.recordedEvents().size();

        room.reactivate();

        assertThat(room.state()).isEqualTo(RoomState.ACTIVE);
        assertThat(room.recordedEvents()).hasSize(eventsBefore);
    }

    @Test
    void reactivate_fromDeactivated_isRejected() {
        Room room = Room.create(name(), LOCATION, CAPACITY);
        room.deactivate();

        assertThatThrownBy(room::reactivate)
                .isInstanceOf(IllegalRoomStateException.class);

        assertThat(room.state()).isEqualTo(RoomState.DEACTIVATED);
    }

    @Test
    void deactivate_fromActive_permanentlyFreezesRoom() {
        Room room = Room.create(name(), LOCATION, CAPACITY);

        room.deactivate();

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
        Room room = Room.create(name(), LOCATION, CAPACITY);
        room.placeUnderMaintenance();

        room.deactivate();

        assertThat(room.state()).isEqualTo(RoomState.DEACTIVATED);
    }

    @Test
    void deactivate_fromDeactivated_isIdempotentNoOp() {
        Room room = Room.create(name(), LOCATION, CAPACITY);
        room.deactivate();
        int eventsBefore = room.recordedEvents().size();

        room.deactivate();

        assertThat(room.state()).isEqualTo(RoomState.DEACTIVATED);
        assertThat(room.recordedEvents()).hasSize(eventsBefore);
    }

    @Test
    void deactivatedRoom_blocksReactivationAndMaintenance_butDeactivateIsSafeNoOp() {
        Room room = Room.create(name(), LOCATION, CAPACITY);
        room.deactivate();

        assertThatThrownBy(room::placeUnderMaintenance).isInstanceOf(IllegalRoomStateException.class);
        assertThatThrownBy(room::reactivate).isInstanceOf(IllegalRoomStateException.class);
        assertThatCode(room::deactivate).doesNotThrowAnyException();
        assertThat(room.state()).isEqualTo(RoomState.DEACTIVATED);
    }

    @Test
    void changeCode_recomputesNameAndEmitsRoomRenamedEvent() {
        Room room = Room.create(name(), LOCATION, CAPACITY);

        room.changeCode("LAB");

        assertThat(room.name()).isEqualTo(RoomName.of(LOCATION, "LAB"));
        assertThat(room.location()).isEqualTo(LOCATION);
        assertThat(room.updatedAt()).isAfterOrEqualTo(room.createdAt());
        assertThat(room.recordedEvents())
                .filteredOn(RoomRenamedEvent.class::isInstance)
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    RoomRenamedEvent ev = (RoomRenamedEvent) e;
                    assertThat(ev.roomId()).isEqualTo(room.id());
                    assertThat(ev.reason()).isEqualTo(RoomRenameReason.CODE_CHANGED);
                    assertThat(ev.oldName()).isEqualTo(name());
                    assertThat(ev.oldCode()).isEqualTo(CODE);
                    assertThat(ev.newName()).isEqualTo(RoomName.of(LOCATION, "LAB"));
                    assertThat(ev.newCode()).isEqualTo("LAB");
                    assertThat(ev.location()).isEqualTo(LOCATION);
                });
    }

    @Test
    void changeCode_preservesBuildingAndFloor() {
        Room room = Room.create(name(), LOCATION, CAPACITY);

        room.changeCode("A1");

        assertThat(room.location().building()).isEqualTo("F");
        assertThat(room.location().floor()).isEqualTo(2);
        assertThat(room.name().asString()).isEqualTo("F.02A1");
    }

    @Test
    void changeCode_rejectsInvalidCode() {
        Room room = Room.create(name(), LOCATION, CAPACITY);

        assertThatThrownBy(() -> room.changeCode("")).isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> room.changeCode("A-B")).isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> room.changeCode("ABCDEFGHIJK")).isInstanceOf(RoomDomainException.class);
    }

    @Test
    void changeCode_sameCode_isIdempotentNoEvent() {
        Room room = Room.create(name(), LOCATION, CAPACITY);
        int before = room.recordedEvents().size();

        room.changeCode(CODE);

        assertThat(room.name()).isEqualTo(name());
        assertThat(room.recordedEvents()).hasSize(before);
    }

    @Test
    void changeCode_fromDeactivated_isRejected() {
        Room room = Room.create(name(), LOCATION, CAPACITY);
        room.deactivate();

        assertThatThrownBy(() -> room.changeCode("LAB"))
                .isInstanceOf(IllegalRoomStateException.class);
        assertThat(room.name()).isEqualTo(name());
    }

    @Test
    void clearDomainEvents_removesRecordedEvents() {
        Room room = Room.create(name(), LOCATION, CAPACITY);
        room.deactivate();

        room.clearDomainEvents();

        assertThat(room.recordedEvents()).isEmpty();
    }

    @Test
    void relocateTo_recomputesNameKeepsCodeAndEmitsLocationChanged() {
        Room room = Room.create(name(), LOCATION, CAPACITY);
        RoomLocation newLocation = RoomLocation.of("G", 3);

        room.relocateTo(newLocation);

        assertThat(room.location()).isEqualTo(newLocation);
        assertThat(room.name()).isEqualTo(RoomName.of(newLocation, CODE));
        assertThat(room.name().code()).isEqualTo(CODE);
        assertThat(room.updatedAt()).isAfterOrEqualTo(room.createdAt());
        assertThat(room.recordedEvents())
                .filteredOn(RoomRenamedEvent.class::isInstance)
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    RoomRenamedEvent ev = (RoomRenamedEvent) e;
                    assertThat(ev.roomId()).isEqualTo(room.id());
                    assertThat(ev.reason()).isEqualTo(RoomRenameReason.LOCATION_CHANGED);
                    assertThat(ev.oldName()).isEqualTo(name());
                    assertThat(ev.oldCode()).isEqualTo(CODE);
                    assertThat(ev.newName()).isEqualTo(RoomName.of(newLocation, CODE));
                    assertThat(ev.newCode()).isEqualTo(CODE);
                    assertThat(ev.location()).isEqualTo(newLocation);
                });
    }

    @Test
    void relocateTo_preservesCode() {
        Room room = Room.create(name(), LOCATION, CAPACITY);

        room.relocateTo(RoomLocation.of("G", 3));

        assertThat(room.name().code()).isEqualTo(CODE);
        assertThat(room.name().asString()).isEqualTo("G.0301");
    }

    @Test
    void relocateTo_rejectsInvalidLocation() {
        Room room = Room.create(name(), LOCATION, CAPACITY);

        assertThatThrownBy(() -> room.relocateTo(RoomLocation.of("G", 0)))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void relocateTo_sameLocation_isIdempotentNoEvent() {
        Room room = Room.create(name(), LOCATION, CAPACITY);
        int before = room.recordedEvents().size();

        room.relocateTo(LOCATION);

        assertThat(room.location()).isEqualTo(LOCATION);
        assertThat(room.recordedEvents()).hasSize(before);
    }

    @Test
    void relocateTo_fromDeactivated_isRejected() {
        Room room = Room.create(name(), LOCATION, CAPACITY);
        room.deactivate();

        assertThatThrownBy(() -> room.relocateTo(RoomLocation.of("G", 3)))
                .isInstanceOf(IllegalRoomStateException.class);
        assertThat(room.location()).isEqualTo(LOCATION);
    }

    @Test
    void changeCapacity_updatesCapacityAndEmitsRoomCapacityChanged() {
        Room room = Room.create(name(), LOCATION, CAPACITY);

        room.changeCapacity(80);

        assertThat(room.capacity()).isEqualTo(80);
        assertThat(room.updatedAt()).isAfterOrEqualTo(room.createdAt());
        assertThat(room.recordedEvents())
                .filteredOn(RoomCapacityChanged.class::isInstance)
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    RoomCapacityChanged ev = (RoomCapacityChanged) e;
                    assertThat(ev.roomId()).isEqualTo(room.id());
                    assertThat(ev.oldCapacity()).isEqualTo(CAPACITY);
                    assertThat(ev.newCapacity()).isEqualTo(80);
                    assertThat(ev.occurredAt()).isEqualTo(room.updatedAt());
                });
    }

    @Test
    void changeCapacity_rejectsNonPositive() {
        Room room = Room.create(name(), LOCATION, CAPACITY);

        assertThatThrownBy(() -> room.changeCapacity(0))
                .isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> room.changeCapacity(-5))
                .isInstanceOf(RoomDomainException.class);
        assertThat(room.capacity()).isEqualTo(CAPACITY);
    }

    @Test
    void changeCapacity_sameCapacity_isIdempotentNoEvent() {
        Room room = Room.create(name(), LOCATION, CAPACITY);
        int before = room.recordedEvents().size();

        room.changeCapacity(CAPACITY);

        assertThat(room.capacity()).isEqualTo(CAPACITY);
        assertThat(room.recordedEvents()).hasSize(before);
    }

    @Test
    void changeCapacity_fromDeactivated_isRejected() {
        Room room = Room.create(name(), LOCATION, CAPACITY);
        room.deactivate();

        assertThatThrownBy(() -> room.changeCapacity(80))
                .isInstanceOf(IllegalRoomStateException.class);
        assertThat(room.capacity()).isEqualTo(CAPACITY);
    }
}
