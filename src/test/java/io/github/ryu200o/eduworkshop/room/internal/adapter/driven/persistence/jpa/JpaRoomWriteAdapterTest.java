package io.github.ryu200o.eduworkshop.room.internal.adapter.driven.persistence.jpa;

import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
}
