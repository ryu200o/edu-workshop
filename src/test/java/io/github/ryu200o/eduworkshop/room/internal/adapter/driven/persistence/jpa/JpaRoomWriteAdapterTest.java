package io.github.ryu200o.eduworkshop.room.internal.adapter.driven.persistence.jpa;

import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomException;
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

    private static Room newRoom() {
        RoomLocation location = RoomLocation.of("F", 2);
        RoomName name = RoomName.of("F-201");
        return Room.create(name, location, 1, 50);
    }

    @Test
    void save_thenLoadById_roundTripsAggregate() {
        Room saved = roomRepository.save(newRoom());

        Optional<Room> loaded = roomRepository.loadById(saved.id());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().name()).isEqualTo(RoomName.of("F-201"));
        assertThat(loaded.get().code()).isEqualTo(1);
        assertThat(loaded.get().location()).isEqualTo(RoomLocation.of("F", 2));
        assertThat(loaded.get().capacity()).isEqualTo(50);
    }

    @Test
    void loadById_whenAbsent_returnsEmpty() {
        assertThat(roomRepository.loadById(RoomId.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void existsByCoordinate_reflectsPersistedRows_viaCompositeKey() {
        RoomLocation location = RoomLocation.of("F", 2);

        assertThat(roomRepository.existsByCoordinate(location.building(), location.floor(), 1)).isFalse();

        roomRepository.save(Room.create(RoomName.of("F-201"), location, 1, 50));

        assertThat(roomRepository.existsByCoordinate(location.building(), location.floor(), 1)).isTrue();
        // Different code at same location must NOT collide.
        assertThat(roomRepository.existsByCoordinate(location.building(), location.floor(), 2)).isFalse();
    }

    @Test
    void existsByCoordinate_reflectsTargetCoordinate() {
        RoomLocation location = RoomLocation.of("F", 2);

        assertThat(roomRepository.existsByCoordinate("F", 2, 2)).isFalse();

        roomRepository.save(Room.create(RoomName.of("F-201"), location, 1, 50));

        assertThat(roomRepository.existsByCoordinate("F", 2, 2)).isFalse();
        assertThat(roomRepository.existsByCoordinate("F", 2, 1)).isTrue();
    }

    @Test
    void loadById_thenChangeCode_roundTripsAndPersistsSilently() {
        Room saved = roomRepository.save(newRoom());

        Optional<Room> loaded = roomRepository.loadById(saved.id());
        assertThat(loaded).isPresent();

        Room room = loaded.get();
        room.changeCode(99);
        roomRepository.save(room);

        Optional<Room> renamed = roomRepository.loadById(saved.id());
        assertThat(renamed).isPresent();
        assertThat(renamed.get().code()).isEqualTo(99);
        assertThat(renamed.get().name()).isEqualTo(RoomName.of("F-201"));
    }

    @Test
    void save_duplicateCoordinate_raceProofGate_throwsDuplicateRoomException() {
        RoomLocation location = RoomLocation.of("F", 2);

        // First room owns the (building, floor, code) coordinate.
        roomRepository.save(Room.create(RoomName.of("F-201"), location, 1, 50));

        // Second room with a DIFFERENT id but the SAME coordinate — simulates a concurrent insert that
        // slipped past the handler's existsByCoordinate (rào lần 1). The DB unique constraint (rào lần 2)
        // must reject it and the adapter must translate it into domain vocabulary.
        Room duplicate = Room.create(RoomId.of(UUID.randomUUID()), RoomName.of("F-202"), location, 1, 50,
                Instant.now(), Instant.now());

        assertThatThrownBy(() -> roomRepository.save(duplicate))
                .isInstanceOf(DuplicateRoomException.class);
    }

    @Test
    void save_duplicateNameInSameLocation_raceProofGate_throwsDuplicateRoomException() {
        RoomLocation location = RoomLocation.of("F", 2);

        // First room owns the (building, floor, name) coordinate.
        roomRepository.save(Room.create(RoomName.of("F-201"), location, 1, 50));

        // Same name (different code) at the same location must collide on uk_rooms_building_floor_name.
        Room duplicate = Room.create(RoomId.of(UUID.randomUUID()), RoomName.of("F-201"), location, 2, 50,
                Instant.now(), Instant.now());

        assertThatThrownBy(() -> roomRepository.save(duplicate))
                .isInstanceOf(DuplicateRoomException.class);
    }
}
