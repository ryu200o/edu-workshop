package io.github.ryu200o.eduworkshop.room.internal.adapter.driven.persistence.jpa;

import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCapacity;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomCodeException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomNameException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.policy.RoomUniquenessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the JPA write adapter — full Spring context + real H2 (PostgreSQL mode) + Flyway,
 * exercised through {@link RoomRepository} (the wiring the application's command handlers actually use).
 */
@SpringBootTest
@Transactional
class JpaRoomWriteAdapterTest {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomUniquenessPolicy uniquenessPolicy;

    // Fixtures bypass the uniqueness gate (already-unique rows): a policy that always reports "unique".
    private static final RoomUniquenessPolicy ALWAYS_UNIQUE = new RoomUniquenessPolicy() {
        @Override
        public boolean isCodeUnique(RoomLocation location, RoomCode code) {
            return true;
        }

        @Override
        public boolean isNameUnique(RoomLocation location, RoomName name) {
            return true;
        }
    };

    private static Room newRoom() {
        RoomLocation location = RoomLocation.of("F", 2);
        RoomName name = RoomName.of("F-201");
        Instant now = Instant.now();
        return Room.create(RoomId.generate(), name, location, RoomCode.of(1), RoomCapacity.of(50),
                now, ALWAYS_UNIQUE);
    }

    @Test
    void save_thenLoadById_roundTripsAggregate() {
        Room saved = roomRepository.save(newRoom());

        Optional<Room> loaded = roomRepository.loadById(saved.id());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().name()).isEqualTo(RoomName.of("F-201"));
        assertThat(loaded.get().code()).isEqualTo(RoomCode.of(1));
        assertThat(loaded.get().location()).isEqualTo(RoomLocation.of("F", 2));
        assertThat(loaded.get().capacity()).isEqualTo(RoomCapacity.of(50));
    }

    @Test
    void loadById_whenAbsent_returnsEmpty() {
        assertThat(roomRepository.loadById(RoomId.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void policy_isCodeUnique_reflectsPersistedRows_viaCompositeKey() {
        RoomLocation location = RoomLocation.of("F", 2);

        assertThat(uniquenessPolicy.isCodeUnique(location, RoomCode.of(1))).isTrue();

        roomRepository.save(newPersistedRoom(RoomName.of("F-201"), location, 1));

        assertThat(uniquenessPolicy.isCodeUnique(location, RoomCode.of(1))).isFalse();
        // Different code at same location must NOT collide.
        assertThat(uniquenessPolicy.isCodeUnique(location, RoomCode.of(2))).isTrue();
    }

    @Test
    void policy_isCodeUnique_reflectsTargetCoordinate() {
        RoomLocation location = RoomLocation.of("F", 2);

        assertThat(uniquenessPolicy.isCodeUnique(location, RoomCode.of(2))).isTrue();

        roomRepository.save(newPersistedRoom(RoomName.of("F-201"), location, 1));

        assertThat(uniquenessPolicy.isCodeUnique(location, RoomCode.of(2))).isTrue();
        assertThat(uniquenessPolicy.isCodeUnique(location, RoomCode.of(1))).isFalse();
    }

    @Test
    void policy_isNameUnique_reflectsPersistedRows_viaCompositeKey() {
        RoomLocation location = RoomLocation.of("F", 2);

        assertThat(uniquenessPolicy.isNameUnique(location, RoomName.of("F-201"))).isTrue();

        roomRepository.save(newPersistedRoom(RoomName.of("F-201"), location, 1));

        assertThat(uniquenessPolicy.isNameUnique(location, RoomName.of("F-201"))).isFalse();
        // Same name at a DIFFERENT location must NOT collide (constraint is scoped by location).
        assertThat(uniquenessPolicy.isNameUnique(RoomLocation.of("G", 3), RoomName.of("F-201"))).isTrue();
    }

    @Test
    void loadById_thenChangeCode_roundTripsAndPersistsSilently() {
        Room saved = roomRepository.save(newRoom());

        Optional<Room> loaded = roomRepository.loadById(saved.id());
        assertThat(loaded).isPresent();

        Room room = loaded.get();
        room.changeCode(RoomCode.of(99), ALWAYS_UNIQUE, Instant.now());
        roomRepository.save(room);

        Optional<Room> renamed = roomRepository.loadById(saved.id());
        assertThat(renamed).isPresent();
        assertThat(renamed.get().code()).isEqualTo(RoomCode.of(99));
        assertThat(renamed.get().name()).isEqualTo(RoomName.of("F-201"));
    }

    @Test
    void save_duplicateCoordinate_raceProofGate_throwsDuplicateRoomCodeException() {
        RoomLocation location = RoomLocation.of("F", 2);

        // First room owns the (building, floor, code) coordinate.
        roomRepository.save(newPersistedRoom(RoomName.of("F-201"), location, 1));

        // Second room with a DIFFERENT id but the SAME coordinate — simulates a concurrent insert that
        // slipped past the policy's isCodeUnique (rào lần 1). The DB unique constraint (rào lần 2)
        // must reject it and the adapter must translate it into domain vocabulary.
        Room duplicate = Room.create(RoomId.of(UUID.randomUUID()), RoomName.of("F-202"), location,
                RoomCode.of(1), RoomCapacity.of(50), Instant.now(), ALWAYS_UNIQUE);

        assertThatThrownBy(() -> roomRepository.save(duplicate))
                .isInstanceOf(DuplicateRoomCodeException.class)
                .hasMessageContaining("code 1");
    }

    @Test
    void save_duplicateNameInSameLocation_raceProofGate_throwsDuplicateRoomNameException() {
        RoomLocation location = RoomLocation.of("F", 2);

        // First room owns the (building, floor, name) coordinate.
        roomRepository.save(newPersistedRoom(RoomName.of("F-201"), location, 1));

        // Same name (different code) at the same location must collide on uk_rooms_building_floor_name.
        Room duplicate = Room.create(RoomId.of(UUID.randomUUID()), RoomName.of("F-201"), location,
                RoomCode.of(2), RoomCapacity.of(50), Instant.now(), ALWAYS_UNIQUE);

        assertThatThrownBy(() -> roomRepository.save(duplicate))
                .isInstanceOf(DuplicateRoomNameException.class)
                .hasMessageContaining("named 'F-201'");
    }

    private Room newPersistedRoom(RoomName name, RoomLocation location, int code) {
        Instant now = Instant.now();
        return roomRepository.save(Room.create(RoomId.generate(), name, location,
                RoomCode.of(code), RoomCapacity.of(50), now, ALWAYS_UNIQUE));
    }
}
