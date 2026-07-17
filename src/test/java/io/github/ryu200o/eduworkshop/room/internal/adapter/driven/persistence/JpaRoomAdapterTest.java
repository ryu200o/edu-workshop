package io.github.ryu200o.eduworkshop.room.internal.adapter.driven.persistence;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.view.RoomDetailView;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.view.RoomSummaryView;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomExistencePort;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomQueryPort;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomStateGateway;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomState;
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
        Optional<RoomDetailView> found = roomQueryPort.findById(room.id());

        assertThat(found).isPresent();
        RoomDetailView response = found.get();
        assertThat(response.id()).isEqualTo(room.id());
        assertThat(response.name()).isEqualTo("F.0201");
        assertThat(response.building()).isEqualTo("F");
        assertThat(response.floor()).isEqualTo(2);
        assertThat(response.capacity()).isEqualTo(50);
        assertThat(response.state()).isEqualTo(RoomState.ACTIVE.name());
    }

    @Test
    void save_thenFindByName_returnsProjection() {
        Room room = newRoom();
        roomStateGateway.save(room);

        Optional<RoomSummaryView> found = roomQueryPort.findByName(RoomName.ofRaw("F.0201"));

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
        assertThat(roomQueryPort.findByName(RoomName.ofRaw("G.0301"))).isEmpty();
    }

    @Test
    void loadById_thenChangeCode_roundTripsAggregateAndPersistsRename() {
        Room saved = roomStateGateway.save(newRoom());

        Optional<Room> loaded = roomStateGateway.loadById(saved.id());
        assertThat(loaded).isPresent();

        Room room = loaded.get();
        assertThat(room.name()).isEqualTo(RoomName.of(RoomLocation.of("F", 2), "01"));
        assertThat(room.location()).isEqualTo(RoomLocation.of("F", 2));
        assertThat(room.capacity()).isEqualTo(50);

        room.changeCode("LAB");
        roomStateGateway.save(room);

        Optional<RoomDetailView> renamed = roomQueryPort.findById(saved.id());
        assertThat(renamed).isPresent();
        assertThat(renamed.get().name()).isEqualTo("F.02LAB");
    }

    @Test
    void loadById_whenAbsent_returnsEmpty() {
        assertThat(roomStateGateway.loadById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void existsByBuildingAndFloorAndCode_reflectsTargetCoordinate() {
        RoomLocation location = RoomLocation.of("F", 2);

        // target (F, 2, "02") is free before any persist
        assertThat(roomExistencePort.existsByBuildingAndFloorAndCode("F", 2, "02")).isFalse();

        roomStateGateway.save(Room.create(RoomName.of(location, "01"), location, 50));

        // same building/floor but different code is free
        assertThat(roomExistencePort.existsByBuildingAndFloorAndCode("F", 2, "02")).isFalse();
        // occupied coordinate collides
        assertThat(roomExistencePort.existsByBuildingAndFloorAndCode("F", 2, "01")).isTrue();
    }
}
