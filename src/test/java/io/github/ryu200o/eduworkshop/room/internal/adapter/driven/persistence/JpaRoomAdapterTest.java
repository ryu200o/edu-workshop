package io.github.ryu200o.eduworkshop.room.internal.adapter.driven.persistence;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.RoomResponse;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomExistencePort;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomQueryPort;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomStateGateway;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.entity.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.state.RoomState;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the JPA persistence slice — full Spring context + real H2 (PostgreSQL mode) +
 * Flyway migrations, exercised through the outbound ports (the wiring the application actually uses).
 */
@SpringBootTest
@Transactional
class JpaRoomAdapterTest {

    @Autowired
    private RoomStateGateway roomStateGateway;

    @Autowired
    private RoomExistencePort roomExistencePort;

    @Autowired
    private RoomQueryPort roomQueryPort;

    private static Room newRoom() {
        RoomLocation location = RoomLocation.of("F", 2);
        RoomName name = RoomName.of(location, "01");
        return Room.create(name, location, 50);
    }

    @Test
    void save_thenFindById_roundTripsThroughDatabase() {
        Room room = newRoom();

        roomStateGateway.save(room);
        Optional<RoomResponse> found = roomQueryPort.findById(room.id());

        assertThat(found).isPresent();
        RoomResponse response = found.get();
        assertThat(response.id()).isEqualTo(room.id());
        assertThat(response.name()).isEqualTo("F.201");
        assertThat(response.building()).isEqualTo("F");
        assertThat(response.floor()).isEqualTo(2);
        assertThat(response.capacity()).isEqualTo(50);
        assertThat(response.state()).isEqualTo(RoomState.ACTIVE.name());
    }

    @Test
    void save_thenFindByName_returnsProjection() {
        Room room = newRoom();
        roomStateGateway.save(room);

        Optional<RoomResponse> found = roomQueryPort.findByName(RoomName.of("F.201"));

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(room.id());
    }

    @Test
    void existsByNameAndLocation_reflectsPersistedRows_viaCompositeKey() {
        RoomLocation location = RoomLocation.of("F", 2);
        RoomName name = RoomName.of(location, "01");

        assertThat(roomExistencePort.existsByNameAndLocation(name, location)).isFalse();

        roomStateGateway.save(Room.create(name, location, 50));

        assertThat(roomExistencePort.existsByNameAndLocation(name, location)).isTrue();
        // Different code at same location must NOT collide.
        assertThat(roomExistencePort.existsByNameAndLocation(
                RoomName.of(location, "02"), location)).isFalse();
    }

    @Test
    void findById_whenAbsent_returnsEmpty() {
        assertThat(roomQueryPort.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByName_whenAbsent_returnsEmpty() {
        assertThat(roomQueryPort.findByName(RoomName.of("G.301"))).isEmpty();
    }
}
