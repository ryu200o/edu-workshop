package io.github.ryu200o.eduworkshop.room.internal.adapter.driven.persistence.jooq;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.view.RoomDetailView;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.view.RoomSummaryView;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomQueryPort;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomState;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the JOOQ read adapter — full Spring context + real H2 (PostgreSQL mode) + Flyway.
 * Proves the read path assembles {@code *View} projections directly from flat SQL columns (no JPA entity,
 * no domain reconstruction — CQRS bypass). Rows are seeded via {@link RoomRepository} (JPA) since this
 * adapter is read-only by design.
 */
@SpringBootTest
class JooqRoomReadAdapterTest {

    @Autowired
    private RoomQueryPort roomQueryPort;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanSchema() {
        // The shared in-memory DB is not rolled back (read test is non-transactional), so reset rows
        // between tests to keep each scenario isolated.
        jdbcTemplate.update("DELETE FROM rooms");
    }

    private static Room newRoom() {
        RoomLocation location = RoomLocation.of("F", 2);
        RoomName name = RoomName.of(location, "01");
        return Room.create(name, location, 50);
    }

    @Test
    void save_thenFindById_roundTripsThroughDatabase() {
        Room room = roomRepository.save(newRoom());

        Optional<RoomDetailView> found = roomQueryPort.findById(room.id());

        assertThat(found).isPresent();
        RoomDetailView response = found.get();
        assertThat(response.id()).isEqualTo(room.id().value());
        assertThat(response.name()).isEqualTo("F.0201");
        assertThat(response.building()).isEqualTo("F");
        assertThat(response.floor()).isEqualTo(2);
        assertThat(response.capacity()).isEqualTo(50);
        assertThat(response.state()).isEqualTo(RoomState.ACTIVE.name());
    }

    @Test
    void save_thenFindByName_returnsProjection() {
        Room room = roomRepository.save(newRoom());

        Optional<RoomSummaryView> found = roomQueryPort.findByName(RoomName.ofRaw("F.0201"));

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(room.id().value());
        assertThat(found.get().name()).isEqualTo("F.0201");
        assertThat(found.get().building()).isEqualTo("F");
        assertThat(found.get().floor()).isEqualTo(2);
    }

    @Test
    void findById_whenAbsent_returnsEmpty() {
        assertThat(roomQueryPort.findById(RoomId.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void findByName_whenAbsent_returnsEmpty() {
        assertThat(roomQueryPort.findByName(RoomName.ofRaw("G.0301"))).isEmpty();
    }
}
