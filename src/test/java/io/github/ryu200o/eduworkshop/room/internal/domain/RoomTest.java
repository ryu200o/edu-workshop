package io.github.ryu200o.eduworkshop.room.internal.domain;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCreated;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCapacityChanged;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRenamedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRelocatedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomStateChanged;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.IllegalRoomStateException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.policy.RoomUniquenessPolicy;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomState;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RoomTest {

    // A stateful fake of the global-uniqueness policy. It models the SET of rooms that already exist
    // (i.e. all OTHER rooms), so the aggregate's invariant enforcement can be exercised for real instead
    // of being hidden behind an ALWAYS_UNIQUE stub. Seed occupied coordinates/names to simulate a
    // collision; leave it empty to mean "everything is unique". Call counters let us assert that the
    // idempotency skip inside the aggregate runs BEFORE the policy is consulted.
    private static final class FakeUniquenessPolicy implements RoomUniquenessPolicy {
        private final Set<CodeKey> occupiedCodes = new HashSet<>();
        private final Set<NameKey> occupiedNames = new HashSet<>();
        private int codeChecks;
        private int nameChecks;

        static FakeUniquenessPolicy unique() {
            return new FakeUniquenessPolicy();
        }

        FakeUniquenessPolicy occupiedCode(RoomLocation location, int code) {
            occupiedCodes.add(new CodeKey(location, code));
            return this;
        }

        FakeUniquenessPolicy occupiedName(RoomLocation location, RoomName name) {
            occupiedNames.add(new NameKey(location, name));
            return this;
        }

        int codeChecks() {
            return codeChecks;
        }

        int nameChecks() {
            return nameChecks;
        }

        @Override
        public boolean isCodeUnique(RoomLocation location, int code) {
            codeChecks++;
            return !occupiedCodes.contains(new CodeKey(location, code));
        }

        @Override
        public boolean isNameUnique(RoomLocation location, RoomName name) {
            nameChecks++;
            return !occupiedNames.contains(new NameKey(location, name));
        }

        private record CodeKey(RoomLocation location, int code) {}
        private record NameKey(RoomLocation location, RoomName name) {}
    }

    private static final RoomLocation LOCATION = RoomLocation.of("F", 2);
    private static final String NAME = "F.0201";
    private static final int CODE = 1;
    private static final int CAPACITY = 50;

    private static RoomName name() {
        return RoomName.of(NAME);
    }

    // Fixture: a room that is unique in the world (empty policy) — used by the behavior tests below.
    private static Room newRoom() {
        return Room.create(name(), LOCATION, CODE, CAPACITY, FakeUniquenessPolicy.unique());
    }

    @Test
    void create_yieldsActiveRoomAndEmitsRoomCreated() {
        Room room = Room.create(name(), LOCATION, CODE, CAPACITY, FakeUniquenessPolicy.unique());

        assertThat(room.id()).isNotNull();
        assertThat(room.name()).isEqualTo(name());
        assertThat(room.code()).isEqualTo(CODE);
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
                    assertThat(created.roomId()).isEqualTo(room.id().value());
                    assertThat(created.name()).isEqualTo(name());
                    assertThat(created.code()).isEqualTo(CODE);
                    assertThat(created.location()).isEqualTo(LOCATION);
                    assertThat(created.initialState()).isEqualTo(RoomState.ACTIVE);
                });
    }

    @Test
    void create_withExplicitIdentity_emitsCreatedEventAndPreservesTimestamps() {
        RoomId roomId = RoomId.of(UUID.randomUUID());
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-01-01T00:00:00Z");

        Room room = Room.create(roomId, name(), LOCATION, CODE, CAPACITY, createdAt, updatedAt, FakeUniquenessPolicy.unique());

        assertThat(room.id()).isEqualTo(roomId);
        assertThat(room.createdAt()).isEqualTo(createdAt);
        assertThat(room.updatedAt()).isEqualTo(updatedAt);
        assertThat(room.recordedEvents()).hasSize(1);
        assertThat(room.recordedEvents().get(0)).isInstanceOf(RoomCreated.class);
    }

    @Test
    void reconstruct_preservesPersistedStateAndTimestamps_withoutEvents() {
        RoomId roomId = RoomId.of(UUID.randomUUID());
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-06-01T00:00:00Z");

        Room room = Room.reconstruct(roomId, name(), LOCATION, CODE, CAPACITY, RoomState.MAINTENANCE, createdAt, updatedAt);

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

        assertThatThrownBy(() -> Room.reconstruct(roomId, name(), LOCATION, CODE, CAPACITY, null, now, now))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void create_rejectsNullName() {
        assertThatThrownBy(() -> Room.create(null, LOCATION, CODE, CAPACITY, FakeUniquenessPolicy.unique()))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void create_rejectsNonPositiveCode() {
        assertThatThrownBy(() -> Room.create(name(), LOCATION, 0, CAPACITY, FakeUniquenessPolicy.unique()))
                .isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> Room.create(name(), LOCATION, -5, CAPACITY, FakeUniquenessPolicy.unique()))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void create_rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> Room.create(name(), LOCATION, CODE, 0, FakeUniquenessPolicy.unique()))
                .isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> Room.create(name(), LOCATION, CODE, -5, FakeUniquenessPolicy.unique()))
                .isInstanceOf(RoomDomainException.class);
    }

    // ── Global invariant enforcement (the point of ADR 0005): the aggregate owns the decision ──

    @Test
    void create_rejectsDuplicateCode_withCorrectReason() {
        var policy = FakeUniquenessPolicy.unique().occupiedCode(LOCATION, CODE);

        DuplicateRoomException ex = catchThrowableOfType(
                () -> Room.create(name(), LOCATION, CODE, CAPACITY, policy), DuplicateRoomException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getReason()).isEqualTo(DuplicateRoomException.Reason.CODE);
    }

    @Test
    void create_rejectsDuplicateName_withCorrectReason() {
        var policy = FakeUniquenessPolicy.unique().occupiedName(LOCATION, name());

        DuplicateRoomException ex = catchThrowableOfType(
                () -> Room.create(name(), LOCATION, CODE, CAPACITY, policy), DuplicateRoomException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getReason()).isEqualTo(DuplicateRoomException.Reason.NAME);
    }

    @Test
    void create_checksBothInvariants_andRejectsOnCodeBeforeName() {
        // Both occupied: the code gate must fire first with the CODE reason.
        var policy = FakeUniquenessPolicy.unique()
                .occupiedCode(LOCATION, CODE)
                .occupiedName(LOCATION, name());

        DuplicateRoomException ex = catchThrowableOfType(
                () -> Room.create(name(), LOCATION, CODE, CAPACITY, policy), DuplicateRoomException.class);

        assertThat(ex.getReason()).isEqualTo(DuplicateRoomException.Reason.CODE);
        assertThat(policy.codeChecks()).isEqualTo(1);
        assertThat(policy.nameChecks()).isEqualTo(0); // short-circuits after code fails
    }

    @Test
    void changeCode_rejectsDuplicateCode_andLeavesStateUnchanged() {
        Room room = newRoom();
        // Another room already owns the SAME location with code 99 (changeCode keeps the location).
        var policy = FakeUniquenessPolicy.unique().occupiedCode(LOCATION, 99);

        DuplicateRoomException ex = catchThrowableOfType(
                () -> room.changeCode(99, policy), DuplicateRoomException.class);

        assertThat(ex.getReason()).isEqualTo(DuplicateRoomException.Reason.CODE);
        assertThat(room.code()).isEqualTo(CODE); // unchanged
        assertThat(room.recordedEvents()).noneMatch(e -> e instanceof RoomRenamedEvent);
    }

    @Test
    void changeCode_rejectsNonPositiveCode() {
        Room room = newRoom();

        assertThatThrownBy(() -> room.changeCode(0, FakeUniquenessPolicy.unique())).isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> room.changeCode(-3, FakeUniquenessPolicy.unique())).isInstanceOf(RoomDomainException.class);
    }

    @Test
    void changeCode_sameCode_isIdempotent_noPolicyCall_noEvent() {
        Room room = newRoom();
        int before = room.recordedEvents().size();
        var policy = FakeUniquenessPolicy.unique();

        room.changeCode(CODE, policy);

        assertThat(room.code()).isEqualTo(CODE);
        assertThat(room.recordedEvents()).hasSize(before);
        assertThat(policy.codeChecks()).isZero(); // idempotency skip runs before the policy
    }

    @Test
    void changeCode_fromDeactivated_isRejected() {
        Room room = newRoom();
        room.deactivate();

        assertThatThrownBy(() -> room.changeCode(99, FakeUniquenessPolicy.unique()))
                .isInstanceOf(IllegalRoomStateException.class);
        assertThat(room.code()).isEqualTo(CODE);
    }

    @Test
    void changeName_rejectsDuplicateName_withCorrectReason() {
        Room room = newRoom();
        var policy = FakeUniquenessPolicy.unique().occupiedName(LOCATION, RoomName.of("LAB-101"));

        DuplicateRoomException ex = catchThrowableOfType(
                () -> room.changeName("LAB-101", policy), DuplicateRoomException.class);

        assertThat(ex.getReason()).isEqualTo(DuplicateRoomException.Reason.NAME);
        assertThat(room.name()).isEqualTo(name()); // unchanged
    }

    @Test
    void changeName_sameName_isIdempotent_noPolicyCall_noEvent() {
        Room room = newRoom();
        int before = room.recordedEvents().size();
        var policy = FakeUniquenessPolicy.unique();

        room.changeName(NAME, policy);

        assertThat(room.name()).isEqualTo(name());
        assertThat(room.recordedEvents()).hasSize(before);
        assertThat(policy.nameChecks()).isZero(); // idempotency skip runs before the policy
    }

    @Test
    void changeName_fromDeactivated_isRejected() {
        Room room = newRoom();
        room.deactivate();

        assertThatThrownBy(() -> room.changeName("LAB-101", FakeUniquenessPolicy.unique()))
                .isInstanceOf(IllegalRoomStateException.class);
        assertThat(room.name()).isEqualTo(name());
    }

    @Test
    void relocateTo_rejectsDuplicateCodeAtTargetLocation() {
        Room room = newRoom();
        RoomLocation target = RoomLocation.of("G", 3);
        var policy = FakeUniquenessPolicy.unique().occupiedCode(target, CODE); // another room owns (G,3,code=1)

        DuplicateRoomException ex = catchThrowableOfType(
                () -> room.relocateTo(target, policy), DuplicateRoomException.class);

        assertThat(ex.getReason()).isEqualTo(DuplicateRoomException.Reason.CODE);
        assertThat(room.location()).isEqualTo(LOCATION); // unchanged
    }

    @Test
    void relocateTo_rejectsDuplicateNameAtTargetLocation() {
        Room room = newRoom();
        RoomLocation target = RoomLocation.of("G", 3);
        var policy = FakeUniquenessPolicy.unique().occupiedName(target, name()); // another room named F.0201 @ G,3

        DuplicateRoomException ex = catchThrowableOfType(
                () -> room.relocateTo(target, policy), DuplicateRoomException.class);

        assertThat(ex.getReason()).isEqualTo(DuplicateRoomException.Reason.NAME);
        assertThat(room.location()).isEqualTo(LOCATION); // unchanged
    }

    @Test
    void relocateTo_sameLocation_isIdempotent_noPolicyCall_noEvent() {
        Room room = newRoom();
        int before = room.recordedEvents().size();
        var policy = FakeUniquenessPolicy.unique();

        room.relocateTo(LOCATION, policy);

        assertThat(room.location()).isEqualTo(LOCATION);
        assertThat(room.recordedEvents()).hasSize(before);
        assertThat(policy.codeChecks()).isZero();
        assertThat(policy.nameChecks()).isZero();
    }

    @Test
    void placeUnderMaintenance_fromActive_transitionsToMaintenanceAndEmitsEvent() {
        Room room = newRoom();

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
                    assertThat(changed.roomId()).isEqualTo(room.id().value());
                });
    }

    @Test
    void placeUnderMaintenance_fromMaintenance_isIdempotentAndEmitsNoEvent() {
        Room room = newRoom();
        room.placeUnderMaintenance();
        int eventsBefore = room.recordedEvents().size();

        room.placeUnderMaintenance();

        assertThat(room.state()).isEqualTo(RoomState.MAINTENANCE);
        assertThat(room.recordedEvents()).hasSize(eventsBefore);
    }

    @Test
    void placeUnderMaintenance_fromDeactivated_isRejected() {
        Room room = newRoom();
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
        Room room = newRoom();
        room.placeUnderMaintenance();

        room.reactivate();

        assertThat(room.state()).isEqualTo(RoomState.ACTIVE);
        assertThat(room.recordedEvents())
                .filteredOn(RoomStateChanged.class::isInstance)
                .hasSize(2);
    }

    @Test
    void reactivate_fromActive_isIdempotentAndEmitsNoEvent() {
        Room room = newRoom();
        int eventsBefore = room.recordedEvents().size();

        room.reactivate();

        assertThat(room.state()).isEqualTo(RoomState.ACTIVE);
        assertThat(room.recordedEvents()).hasSize(eventsBefore);
    }

    @Test
    void reactivate_fromDeactivated_isRejected() {
        Room room = newRoom();
        room.deactivate();

        assertThatThrownBy(room::reactivate)
                .isInstanceOf(IllegalRoomStateException.class);

        assertThat(room.state()).isEqualTo(RoomState.DEACTIVATED);
    }

    @Test
    void deactivate_fromActive_permanentlyFreezesRoom() {
        Room room = newRoom();

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
        Room room = newRoom();
        room.placeUnderMaintenance();

        room.deactivate();

        assertThat(room.state()).isEqualTo(RoomState.DEACTIVATED);
    }

    @Test
    void deactivate_fromDeactivated_isIdempotentNoOp() {
        Room room = newRoom();
        room.deactivate();
        int eventsBefore = room.recordedEvents().size();

        room.deactivate();

        assertThat(room.state()).isEqualTo(RoomState.DEACTIVATED);
        assertThat(room.recordedEvents()).hasSize(eventsBefore);
    }

    @Test
    void deactivatedRoom_blocksReactivationAndMaintenance_butDeactivateIsSafeNoOp() {
        Room room = newRoom();
        room.deactivate();

        assertThatThrownBy(room::placeUnderMaintenance).isInstanceOf(IllegalRoomStateException.class);
        assertThatThrownBy(room::reactivate).isInstanceOf(IllegalRoomStateException.class);
        assertThatCode(room::deactivate).doesNotThrowAnyException();
        assertThat(room.state()).isEqualTo(RoomState.DEACTIVATED);
    }

    @Test
    void changeCode_changesCodeSilently_noEvent() {
        Room room = newRoom();

        room.changeCode(99, FakeUniquenessPolicy.unique());

        assertThat(room.code()).isEqualTo(99);
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

        room.changeName("LAB-101", FakeUniquenessPolicy.unique());

        assertThat(room.name()).isEqualTo(RoomName.of("LAB-101"));
        assertThat(room.location()).isEqualTo(LOCATION);
        assertThat(room.code()).isEqualTo(CODE);
        assertThat(room.updatedAt()).isAfterOrEqualTo(room.createdAt());
        assertThat(room.recordedEvents())
                .filteredOn(RoomRenamedEvent.class::isInstance)
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    RoomRenamedEvent ev = (RoomRenamedEvent) e;
                    assertThat(ev.roomId()).isEqualTo(room.id().value());
                    assertThat(ev.oldName()).isEqualTo(name());
                    assertThat(ev.newName()).isEqualTo(RoomName.of("LAB-101"));
                });
    }

    @Test
    void clearDomainEvents_removesRecordedEvents() {
        Room room = newRoom();
        room.deactivate();

        room.clearDomainEvents();

        assertThat(room.recordedEvents()).isEmpty();
    }

    @Test
    void relocateTo_keepsNameAndCodeAndEmitsLocationChanged() {
        Room room = newRoom();
        RoomLocation newLocation = RoomLocation.of("G", 3);

        room.relocateTo(newLocation, FakeUniquenessPolicy.unique());

        assertThat(room.location()).isEqualTo(newLocation);
        assertThat(room.name()).isEqualTo(name());
        assertThat(room.code()).isEqualTo(CODE);
        assertThat(room.updatedAt()).isAfterOrEqualTo(room.createdAt());
        assertThat(room.recordedEvents())
                .filteredOn(RoomRelocatedEvent.class::isInstance)
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    RoomRelocatedEvent ev = (RoomRelocatedEvent) e;
                    assertThat(ev.roomId()).isEqualTo(room.id().value());
                    assertThat(ev.oldLocation()).isEqualTo(LOCATION);
                    assertThat(ev.newLocation()).isEqualTo(newLocation);
                });
    }

    @Test
    void relocateTo_preservesNameAndCode() {
        Room room = newRoom();

        room.relocateTo(RoomLocation.of("G", 3), FakeUniquenessPolicy.unique());

        assertThat(room.name()).isEqualTo(name());
        assertThat(room.code()).isEqualTo(CODE);
    }

    @Test
    void relocateTo_rejectsInvalidLocation() {
        Room room = newRoom();

        assertThatThrownBy(() -> room.relocateTo(RoomLocation.of("G", 0), FakeUniquenessPolicy.unique()))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void relocateTo_fromDeactivated_isRejected() {
        Room room = newRoom();
        room.deactivate();

        assertThatThrownBy(() -> room.relocateTo(RoomLocation.of("G", 3), FakeUniquenessPolicy.unique()))
                .isInstanceOf(IllegalRoomStateException.class);
        assertThat(room.location()).isEqualTo(LOCATION);
    }

    @Test
    void changeCapacity_updatesCapacityAndEmitsRoomCapacityChanged() {
        Room room = newRoom();

        room.changeCapacity(80);

        assertThat(room.capacity()).isEqualTo(80);
        assertThat(room.updatedAt()).isAfterOrEqualTo(room.createdAt());
        assertThat(room.recordedEvents())
                .filteredOn(RoomCapacityChanged.class::isInstance)
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    RoomCapacityChanged ev = (RoomCapacityChanged) e;
                    assertThat(ev.roomId()).isEqualTo(room.id().value());
                    assertThat(ev.oldCapacity()).isEqualTo(CAPACITY);
                    assertThat(ev.newCapacity()).isEqualTo(80);
                    assertThat(ev.occurredAt()).isEqualTo(room.updatedAt());
                });
    }

    @Test
    void changeCapacity_rejectsNonPositive() {
        Room room = newRoom();

        assertThatThrownBy(() -> room.changeCapacity(0))
                .isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> room.changeCapacity(-5))
                .isInstanceOf(RoomDomainException.class);
        assertThat(room.capacity()).isEqualTo(CAPACITY);
    }

    @Test
    void changeCapacity_sameCapacity_isIdempotentNoEvent() {
        Room room = newRoom();
        int before = room.recordedEvents().size();

        room.changeCapacity(CAPACITY);

        assertThat(room.capacity()).isEqualTo(CAPACITY);
        assertThat(room.recordedEvents()).hasSize(before);
    }

    @Test
    void changeCapacity_fromDeactivated_isRejected() {
        Room room = newRoom();
        room.deactivate();

        assertThatThrownBy(() -> room.changeCapacity(80))
                .isInstanceOf(IllegalRoomStateException.class);
        assertThat(room.capacity()).isEqualTo(CAPACITY);
    }
}
