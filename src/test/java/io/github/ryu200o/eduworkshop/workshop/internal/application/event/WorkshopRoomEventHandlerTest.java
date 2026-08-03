package io.github.ryu200o.eduworkshop.workshop.internal.application.event;

import io.github.ryu200o.eduworkshop.room.contract.RoomMaintenanceScheduledIntegrationEvent;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the maintenance-flagging path of {@link WorkshopRoomEventHandler} — full
 * Spring context + real H2 + Flyway. Seeds PUBLISHED workshops via {@link WorkshopRepository}, then
 * invokes {@link WorkshopRoomEventHandler#handleIntegrationEvent} directly (it runs in its own
 * {@code REQUIRES_NEW} transaction) and asserts the eviction notice is persisted
 * ({@code is_room_evicted = true}) without changing the workshop state (Titik 2).
 */
@SpringBootTest
class WorkshopRoomEventHandlerTest {

    private static final UUID ROOM_A = UUID.randomUUID();
    private static final Instant WS_START = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant WS_END = Instant.parse("2026-09-01T11:00:00Z");
    private static final Instant MAINT_START = Instant.parse("2026-09-01T08:00:00Z");
    private static final Instant MAINT_END = Instant.parse("2026-09-01T12:00:00Z");

    @Autowired
    private WorkshopRepository workshopRepository;

    @Autowired
    private WorkshopRoomEventHandler handler;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanSchema() {
        jdbcTemplate.update("DELETE FROM workshops");
    }

    private void save(Workshop workshop) {
        transactionTemplate.executeWithoutResult(status -> workshopRepository.save(workshop));
    }

    private WorkshopId savePublishedWorkshop(UUID roomId, Instant start, Instant end) {
        Workshop workshop = publishedWorkshop(roomId, start, end);
        save(workshop);
        return workshop.id();
    }

    private Workshop publishedWorkshop(UUID roomId, Instant start, Instant end) {
        WorkshopId id = WorkshopId.generate();
        Workshop workshop = Workshop.create(
                id,
                WorkshopTitle.of("Intro to AI"),
                WorkshopDescription.of("A beginner workshop"),
                start, end, WorkshopCapacity.of(30), Instant.parse("2026-08-01T00:00:00Z"));
        workshop.plan(RoomReference.of(roomId, "Room A", "Floor 1", 50), false, Instant.parse("2026-08-01T00:00:01Z"));
        workshop.publish(Instant.parse("2026-08-01T00:00:02Z"), 50);
        return workshop;
    }

    private RoomMaintenanceScheduledIntegrationEvent maintenanceEvent(UUID roomId, Instant start, Instant end) {
        return new RoomMaintenanceScheduledIntegrationEvent(
                UUID.randomUUID(), roomId, start, end, "Deep cleaning of the venue", Instant.parse("2026-08-01T01:00:00Z"));
    }

    @Test
    void handle_overlappingPublishedWorkshop_setsEvictionFlagInDbWithoutChangingState() {
        WorkshopId id = savePublishedWorkshop(ROOM_A, WS_START, WS_END);

        handler.handleIntegrationEvent(maintenanceEvent(ROOM_A, MAINT_START, MAINT_END));

        Boolean isRoomEvicted = jdbcTemplate.queryForObject(
                "SELECT is_room_evicted FROM workshops WHERE id = ?", Boolean.class, id.value());
        Instant roomEvictedAt = jdbcTemplate.queryForObject(
                "SELECT room_evicted_at FROM workshops WHERE id = ?", Instant.class, id.value());
        String state = jdbcTemplate.queryForObject(
                "SELECT state FROM workshops WHERE id = ?", String.class, id.value());

        assertThat(isRoomEvicted).isTrue();
        assertThat(roomEvictedAt).isNotNull();
        assertThat(state).isEqualTo("PUBLISHED");
    }

    @Test
    void handle_isIdempotentWhenWorkshopAlreadyEvicted() {
        WorkshopId id = savePublishedWorkshop(ROOM_A, WS_START, WS_END);

        handler.handleIntegrationEvent(maintenanceEvent(ROOM_A, MAINT_START, MAINT_END));
        Instant firstEvictedAt = jdbcTemplate.queryForObject(
                "SELECT room_evicted_at FROM workshops WHERE id = ?", Instant.class, id.value());

        handler.handleIntegrationEvent(maintenanceEvent(ROOM_A, MAINT_START, MAINT_END));
        Instant secondEvictedAt = jdbcTemplate.queryForObject(
                "SELECT room_evicted_at FROM workshops WHERE id = ?", Instant.class, id.value());

        assertThat(secondEvictedAt).isEqualTo(firstEvictedAt);
    }

    @Test
    void handle_nonOverlappingWorkshop_isNotFlagged() {
        WorkshopId id = savePublishedWorkshop(ROOM_A, WS_START, WS_END);

        handler.handleIntegrationEvent(maintenanceEvent(ROOM_A, Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-01T12:00:00Z")));

        Boolean isRoomEvicted = jdbcTemplate.queryForObject(
                "SELECT is_room_evicted FROM workshops WHERE id = ?", Boolean.class, id.value());
        assertThat(isRoomEvicted).isFalse();
    }

    @Test
    void handle_noOverlappingWorkshops_doesNothing() {
        savePublishedWorkshop(ROOM_A, WS_START, WS_END);
        UUID otherRoom = UUID.randomUUID();

        handler.handleIntegrationEvent(maintenanceEvent(otherRoom, MAINT_START, MAINT_END));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workshops WHERE is_room_evicted = TRUE", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void handle_indefiniteMaintenance_matchesWorkshopsStartingAfterMaintenanceStart() {
        WorkshopId id = savePublishedWorkshop(ROOM_A, WS_START, WS_END);

        handler.handleIntegrationEvent(maintenanceEvent(ROOM_A, MAINT_START, null));

        Boolean isRoomEvicted = jdbcTemplate.queryForObject(
                "SELECT is_room_evicted FROM workshops WHERE id = ?", Boolean.class, id.value());
        assertThat(isRoomEvicted).isTrue();
    }

    @Test
    void handle_plannedWorkshop_isNotFlagged() {
        WorkshopId id = WorkshopId.generate();
        Workshop planned = Workshop.create(
                id,
                WorkshopTitle.of("Intro to AI"),
                WorkshopDescription.of("A beginner workshop"),
                WS_START, WS_END, WorkshopCapacity.of(30), Instant.parse("2026-08-01T00:00:00Z"));
        planned.plan(RoomReference.of(ROOM_A, "Room A", "Floor 1", 50), false, Instant.parse("2026-08-01T00:00:01Z"));
        save(planned);

        handler.handleIntegrationEvent(maintenanceEvent(ROOM_A, MAINT_START, MAINT_END));

        Boolean isRoomEvicted = jdbcTemplate.queryForObject(
                "SELECT is_room_evicted FROM workshops WHERE id = ?", Boolean.class, id.value());
        assertThat(isRoomEvicted).isFalse();
    }
}
