package io.github.ryu200o.eduworkshop.room.internal.adapter.outbound.persistence.jpa;

import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCapacity;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.DuplicateRoomCodeException;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.DuplicateRoomNameException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class JpaRoomWriteAdapterTest {

    @Autowired
    private RoomRepository roomRepository;

    private static Room newRoom() {
        RoomLocation location = RoomLocation.of("F", 2);
        RoomName name = RoomName.of("F-201");
        Instant now = Instant.now();
        return Room.create(RoomId.generate(), name, location, RoomCode.of(1), RoomCapacity.of(50), now);
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
    void existsByCoordinate_reflectsPersistedRows() {
        RoomLocation location = RoomLocation.of("F", 2);

        assertThat(roomRepository.existsByCoordinate(location, RoomCode.of(1))).isFalse();

        roomRepository.save(newPersistedRoom(RoomName.of("F-201"), location, 1));

        assertThat(roomRepository.existsByCoordinate(location, RoomCode.of(1))).isTrue();
        assertThat(roomRepository.existsByCoordinate(location, RoomCode.of(2))).isFalse();
    }

    @Test
    void existsByName_reflectsPersistedRows() {
        RoomLocation location = RoomLocation.of("F", 2);

        assertThat(roomRepository.existsByName(location, RoomName.of("F-201"))).isFalse();

        roomRepository.save(newPersistedRoom(RoomName.of("F-201"), location, 1));

        assertThat(roomRepository.existsByName(location, RoomName.of("F-201"))).isTrue();
        assertThat(roomRepository.existsByName(RoomLocation.of("G", 3), RoomName.of("F-201"))).isFalse();
    }

    @Test
    void loadById_thenChangeCode_roundTripsAndPersistsSilently() {
        Room saved = roomRepository.save(newRoom());

        Optional<Room> loaded = roomRepository.loadById(saved.id());
        assertThat(loaded).isPresent();

        Room room = loaded.get();
        room.changeCode(RoomCode.of(99), Instant.now());
        roomRepository.save(room);

        Optional<Room> renamed = roomRepository.loadById(saved.id());
        assertThat(renamed).isPresent();
        assertThat(renamed.get().code()).isEqualTo(RoomCode.of(99));
        assertThat(renamed.get().name()).isEqualTo(RoomName.of("F-201"));
    }

    @Test
    void save_duplicateCoordinate_raceProofGate_throwsDuplicateRoomCodeException() {
        RoomLocation location = RoomLocation.of("F", 2);

        roomRepository.save(newPersistedRoom(RoomName.of("F-201"), location, 1));

        Room duplicate = Room.create(RoomId.of(UUID.randomUUID()), RoomName.of("F-202"), location,
                RoomCode.of(1), RoomCapacity.of(50), Instant.now());

        assertThatThrownBy(() -> roomRepository.save(duplicate))
                .isInstanceOf(DuplicateRoomCodeException.class)
                .hasMessageContaining("code 1");
    }

    @Test
    void save_duplicateNameInSameLocation_raceProofGate_throwsDuplicateRoomNameException() {
        RoomLocation location = RoomLocation.of("F", 2);

        roomRepository.save(newPersistedRoom(RoomName.of("F-201"), location, 1));

        Room duplicate = Room.create(RoomId.of(UUID.randomUUID()), RoomName.of("F-201"), location,
                RoomCode.of(2), RoomCapacity.of(50), Instant.now());

        assertThatThrownBy(() -> roomRepository.save(duplicate))
                .isInstanceOf(DuplicateRoomNameException.class)
                .hasMessageContaining("named 'F-201'");
    }

    private Room newPersistedRoom(RoomName name, RoomLocation location, int code) {
        Instant now = Instant.now();
        return roomRepository.save(Room.create(RoomId.generate(), name, location,
                RoomCode.of(code), RoomCapacity.of(50), now));
    }
}
