package io.github.ryu200o.eduworkshop.room.internal.domain;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.entity.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCreated;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomStateChanged;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.IllegalRoomStateException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.state.RoomState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RoomTest {

    private static final String NAME = "Auditorium A";
    private static final int CAPACITY = 50;
    private static final String LOCATION = "Building B, Floor 3";

    @Test
    void create_yieldsActiveRoomAndEmitsRoomCreated() {
        Room room = Room.create(NAME, CAPACITY, LOCATION);

        assertThat(room.id()).isNotNull();
        assertThat(room.name()).isEqualTo(NAME);
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
                    assertThat(created.initialState()).isEqualTo(RoomState.ACTIVE);
                });
    }

    @Test
    void placeUnderMaintenance_fromActive_transitionsToMaintenanceAndEmitsEvent() {
        Room room = Room.create(NAME, CAPACITY, LOCATION);

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
        Room room = Room.create(NAME, CAPACITY, LOCATION);
        room.placeUnderMaintenance();
        int eventsBefore = room.recordedEvents().size();

        room.placeUnderMaintenance();

        assertThat(room.state()).isEqualTo(RoomState.MAINTENANCE);
        assertThat(room.recordedEvents()).hasSize(eventsBefore);
    }

    @Test
    void placeUnderMaintenance_fromDeactivated_isRejected() {
        Room room = Room.create(NAME, CAPACITY, LOCATION);
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
        Room room = Room.create(NAME, CAPACITY, LOCATION);
        room.placeUnderMaintenance();

        room.reactivate();

        assertThat(room.state()).isEqualTo(RoomState.ACTIVE);
        assertThat(room.recordedEvents())
                .filteredOn(RoomStateChanged.class::isInstance)
                .hasSize(2); // ACTIVE->MAINTENANCE, then MAINTENANCE->ACTIVE
    }

    @Test
    void reactivate_fromActive_isIdempotentAndEmitsNoEvent() {
        Room room = Room.create(NAME, CAPACITY, LOCATION);
        int eventsBefore = room.recordedEvents().size();

        room.reactivate();

        assertThat(room.state()).isEqualTo(RoomState.ACTIVE);
        assertThat(room.recordedEvents()).hasSize(eventsBefore);
    }

    @Test
    void reactivate_fromDeactivated_isRejected() {
        Room room = Room.create(NAME, CAPACITY, LOCATION);
        room.deactivate();

        assertThatThrownBy(room::reactivate)
                .isInstanceOf(IllegalRoomStateException.class)
                .satisfies(e -> assertThat(((IllegalRoomStateException) e).getCurrentState())
                        .isEqualTo(RoomState.DEACTIVATED));

        assertThat(room.state()).isEqualTo(RoomState.DEACTIVATED);
    }

    @Test
    void deactivate_fromActive_permanentlyFreezesRoom() {
        Room room = Room.create(NAME, CAPACITY, LOCATION);

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
        Room room = Room.create(NAME, CAPACITY, LOCATION);
        room.placeUnderMaintenance();

        room.deactivate();

        assertThat(room.state()).isEqualTo(RoomState.DEACTIVATED);
    }

    @Test
    void deactivate_fromDeactivated_isIdempotentNoOp() {
        Room room = Room.create(NAME, CAPACITY, LOCATION);
        room.deactivate();
        int eventsBefore = room.recordedEvents().size();

        room.deactivate();

        assertThat(room.state()).isEqualTo(RoomState.DEACTIVATED);
        assertThat(room.recordedEvents()).hasSize(eventsBefore);
    }

    @Test
    void deactivatedRoom_blocksReactivationAndMaintenance_butDeactivateIsSafeNoOp() {
        Room room = Room.create(NAME, CAPACITY, LOCATION);
        room.deactivate();

        assertThatThrownBy(room::placeUnderMaintenance).isInstanceOf(IllegalRoomStateException.class);
        assertThatThrownBy(room::reactivate).isInstanceOf(IllegalRoomStateException.class);
        // deactivating an already-deactivated room is idempotent, not an error
        assertThatCode(room::deactivate).doesNotThrowAnyException();
        assertThat(room.state()).isEqualTo(RoomState.DEACTIVATED);
    }

    @Test
    void create_rejectsBlankName() {
        assertThatThrownBy(() -> Room.create("  ", CAPACITY, LOCATION))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void create_rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> Room.create(NAME, 0, LOCATION))
                .isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> Room.create(NAME, -5, LOCATION))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void create_rejectsBlankLocation() {
        assertThatThrownBy(() -> Room.create(NAME, CAPACITY, ""))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void clearDomainEvents_removesRecordedEvents() {
        Room room = Room.create(NAME, CAPACITY, LOCATION);
        room.deactivate();

        room.clearDomainEvents();

        assertThat(room.recordedEvents()).isEmpty();
    }

    @Test
    void create_withExplicitIdentity_emitsCreatedEventAndPreservesTimestamps() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-01-01T00:00:00Z");

        Room room = Room.create(id, NAME, CAPACITY, LOCATION, createdAt, updatedAt);

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

        Room room = Room.reconstruct(id, NAME, CAPACITY, LOCATION, RoomState.MAINTENANCE, createdAt, updatedAt);

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

        assertThatThrownBy(() -> Room.reconstruct(id, NAME, CAPACITY, LOCATION, null, now, now))
                .isInstanceOf(RoomDomainException.class);
    }
}
