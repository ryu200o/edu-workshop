package io.github.ryu200o.eduworkshop.workshop.internal.application.event;

import io.github.ryu200o.eduworkshop.room.contract.RoomCapacityChangedIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomMaintenanceScheduledIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomRelocatedIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomRenamedIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomStateChangedIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomStateContract;
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
 * Integration test for {@link WorkshopRoomEventHandler} — full Spring context + real H2 + Flyway.
 * Seeds workshops via {@link WorkshopRepository}, then invokes
 * {@link WorkshopRoomEventHandler#handleIntegrationEvent} directly (it runs in its own
 * {@code REQUIRES_NEW} transaction) and asserts the resulting persistence:
 * <ul>
 *   <li>maintenance-flagging path (Titik 2): {@code is_room_evicted = true} without changing state;</li>
 *   <li>snapshot-refresh paths (rename / relocate / capacity): the {@code *_snapshot} columns are
 *       batch-updated for every workshop of the room (3-Phase, no N+1 save).</li>
 *   <li>state-change path (maintenance / active / deactivated): warnings are set/cleared and
 *       deactivated rooms return workshops to {@code DRAFT}.</li>
 * </ul>
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

    private WorkshopId savePlannedWorkshop(UUID roomId, Instant start, Instant end) {
        WorkshopId id = WorkshopId.generate();
        Workshop workshop = Workshop.create(
                id,
                WorkshopTitle.of("Intro to AI"),
                WorkshopDescription.of("A beginner workshop"),
                start, end, WorkshopCapacity.of(30), Instant.parse("2026-08-01T00:00:00Z"));
        workshop.plan(RoomReference.of(roomId, "Room A", "Floor 1", 50), false, Instant.parse("2026-08-01T00:00:01Z"));
        save(workshop);
        return id;
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

    @Test
    void handleRenamed_refreshesNameSnapshotForAllWorkshopsInRoom() {
        WorkshopId idA = savePublishedWorkshop(ROOM_A, WS_START, WS_END);
        WorkshopId idB = savePublishedWorkshop(ROOM_A, WS_START.plusSeconds(3600), WS_END.plusSeconds(3600));
        UUID otherRoom = UUID.randomUUID();
        savePublishedWorkshop(otherRoom, WS_START, WS_END);

        handler.handleIntegrationEvent(new RoomRenamedIntegrationEvent(
                ROOM_A, "Room A", "Room A Prime", Instant.parse("2026-08-01T01:00:00Z")));

        assertThat(roomNameSnapshot(idA)).isEqualTo("Room A Prime");
        assertThat(roomNameSnapshot(idB)).isEqualTo("Room A Prime");
        assertThat(roomNameSnapshotOfOtherRoom(otherRoom)).isEqualTo("Room A");
    }

    @Test
    void handleRenamed_skipsWorkshopsWithoutRoomReference() {
        WorkshopId id = WorkshopId.generate();
        Workshop draft = Workshop.create(
                id,
                WorkshopTitle.of("Intro to AI"),
                WorkshopDescription.of("A beginner workshop"),
                WS_START, WS_END, WorkshopCapacity.of(30), Instant.parse("2026-08-01T00:00:00Z"));
        save(draft);

        handler.handleIntegrationEvent(new RoomRenamedIntegrationEvent(
                ROOM_A, "Room A", "Room A Prime", Instant.parse("2026-08-01T01:00:00Z")));

        String title = jdbcTemplate.queryForObject(
                "SELECT title FROM workshops WHERE id = ?", String.class, id.value());
        assertThat(title).isEqualTo("Intro to AI");
    }

    @Test
    void handleRelocated_refreshesLocationSnapshotForAllWorkshopsInRoom() {
        WorkshopId idA = savePublishedWorkshop(ROOM_A, WS_START, WS_END);
        WorkshopId idB = savePublishedWorkshop(ROOM_A, WS_START.plusSeconds(3600), WS_END.plusSeconds(3600));

        handler.handleIntegrationEvent(new RoomRelocatedIntegrationEvent(
                ROOM_A, "Floor 1", "Floor 2", Instant.parse("2026-08-01T01:00:00Z")));

        assertThat(roomLocationSnapshot(idA)).isEqualTo("Floor 2");
        assertThat(roomLocationSnapshot(idB)).isEqualTo("Floor 2");
    }

    @Test
    void handleCapacityChanged_refreshesCapacitySnapshotForAllWorkshopsInRoom() {
        WorkshopId idA = savePublishedWorkshop(ROOM_A, WS_START, WS_END);
        WorkshopId idB = savePublishedWorkshop(ROOM_A, WS_START.plusSeconds(3600), WS_END.plusSeconds(3600));

        handler.handleIntegrationEvent(new RoomCapacityChangedIntegrationEvent(
                ROOM_A, 50, 80, Instant.parse("2026-08-01T01:00:00Z")));

        assertThat(roomCapacitySnapshot(idA)).isEqualTo(80);
        assertThat(roomCapacitySnapshot(idB)).isEqualTo(80);
    }

    @Test
    void handleStateChanged_maintenance_setsWarningOnPlannedWorkshops() {
        WorkshopId idA = savePlannedWorkshop(ROOM_A, WS_START, WS_END);
        WorkshopId idB = savePlannedWorkshop(ROOM_A, WS_START.plusSeconds(3600), WS_END.plusSeconds(3600));

        handler.handleIntegrationEvent(new RoomStateChangedIntegrationEvent(
                ROOM_A, RoomStateContract.ACTIVE, RoomStateContract.MAINTENANCE,
                Instant.parse("2026-08-01T01:00:00Z")));

        assertThat(hasRoomWarning(idA)).isTrue();
        assertThat(hasRoomWarning(idB)).isTrue();
    }

    @Test
    void handleStateChanged_backToActive_clearsMaintenanceWarning() {
        WorkshopId idA = savePlannedWorkshop(ROOM_A, WS_START, WS_END);
        jdbcTemplate.update("UPDATE workshops SET has_room_warning = TRUE WHERE id = ?", idA.value());

        handler.handleIntegrationEvent(new RoomStateChangedIntegrationEvent(
                ROOM_A, RoomStateContract.MAINTENANCE, RoomStateContract.ACTIVE,
                Instant.parse("2026-08-01T01:00:00Z")));

        assertThat(hasRoomWarning(idA)).isFalse();
    }

    @Test
    void handleStateChanged_deactivated_returnsPlannedWorkshopsToDraft() {
        WorkshopId idA = savePlannedWorkshop(ROOM_A, WS_START, WS_END);

        handler.handleIntegrationEvent(new RoomStateChangedIntegrationEvent(
                ROOM_A, RoomStateContract.ACTIVE, RoomStateContract.DEACTIVATED,
                Instant.parse("2026-08-01T01:00:00Z")));

        assertThat(workshopState(idA)).isEqualTo("DRAFT");
        assertThat(roomNameSnapshot(idA)).isNull();
    }

    private String roomNameSnapshot(WorkshopId id) {
        return jdbcTemplate.queryForObject(
                "SELECT room_name_snapshot FROM workshops WHERE id = ?", String.class, id.value());
    }

    private String roomNameSnapshotOfOtherRoom(UUID otherRoomId) {
        return jdbcTemplate.queryForObject(
                "SELECT room_name_snapshot FROM workshops WHERE room_id = ?", String.class, otherRoomId);
    }

    private String roomLocationSnapshot(WorkshopId id) {
        return jdbcTemplate.queryForObject(
                "SELECT room_location_snapshot FROM workshops WHERE id = ?", String.class, id.value());
    }

    private int roomCapacitySnapshot(WorkshopId id) {
        return jdbcTemplate.queryForObject(
                "SELECT room_capacity_snapshot FROM workshops WHERE id = ?", Integer.class, id.value());
    }

    private boolean hasRoomWarning(WorkshopId id) {
        Boolean warning = jdbcTemplate.queryForObject(
                "SELECT has_room_warning FROM workshops WHERE id = ?", Boolean.class, id.value());
        return Boolean.TRUE.equals(warning);
    }

    private String workshopState(WorkshopId id) {
        return jdbcTemplate.queryForObject(
                "SELECT state FROM workshops WHERE id = ?", String.class, id.value());
    }
}
