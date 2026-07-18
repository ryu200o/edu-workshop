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
        RoomName name = RoomName.of(location, "01");
        return Room.create(name, location, 50);
    }

    @Test
    void save_thenLoadById_roundTripsAggregate() {
        Room saved = roomRepository.save(newRoom());

        Optional<Room> loaded = roomRepository.loadById(saved.id());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().name()).isEqualTo(RoomName.of(RoomLocation.of("F", 2), "01"));
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
        RoomName name = RoomName.of(location, "01");

        assertThat(roomRepository.existsByCoordinate(location.building(), location.floor(), name.code())).isFalse();

        roomRepository.save(Room.create(name, location, 50));

        assertThat(roomRepository.existsByCoordinate(location.building(), location.floor(), name.code())).isTrue();
        // Different code at same location must NOT collide.
        assertThat(roomRepository.existsByCoordinate(location.building(), location.floor(), "02")).isFalse();
    }

    @Test
    void existsByCoordinate_reflectsTargetCoordinate() {
        RoomLocation location = RoomLocation.of("F", 2);

        assertThat(roomRepository.existsByCoordinate("F", 2, "02")).isFalse();

        roomRepository.save(Room.create(RoomName.of(location, "01"), location, 50));

        assertThat(roomRepository.existsByCoordinate("F", 2, "02")).isFalse();
        assertThat(roomRepository.existsByCoordinate("F", 2, "01")).isTrue();
    }

    @Test
    void loadById_thenChangeCode_roundTripsAndPersistsRename() {
        Room saved = roomRepository.save(newRoom());

        Optional<Room> loaded = roomRepository.loadById(saved.id());
        assertThat(loaded).isPresent();

        Room room = loaded.get();
        room.changeCode("LAB");
        roomRepository.save(room);

        Optional<Room> renamed = roomRepository.loadById(saved.id());
        assertThat(renamed).isPresent();
        assertThat(renamed.get().name()).isEqualTo(RoomName.of(RoomLocation.of("F", 2), "LAB"));
    }

    @Test
    void save_duplicateCoordinate_raceProofGate_throwsDuplicateRoomException() {
        RoomLocation location = RoomLocation.of("F", 2);
        RoomName name = RoomName.of(location, "01");

        // First room owns the (building, floor, code) coordinate.
        roomRepository.save(Room.create(name, location, 50));

        // Second room with a DIFFERENT id but the SAME coordinate — simulates a concurrent insert that
        // slipped past the handler's existsByCoordinate (rào lần 1). The DB unique constraint (rào lần 2)
        // must reject it and the adapter must translate it into domain vocabulary.
        Room duplicate = Room.create(RoomId.of(UUID.randomUUID()), name, location, 50,
                java.time.Instant.now(), java.time.Instant.now());

        assertThatThrownBy(() -> roomRepository.save(duplicate))
                .isInstanceOf(DuplicateRoomException.class);
    }
}
