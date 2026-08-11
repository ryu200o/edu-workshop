package io.github.ryu200o.eduworkshop.workshop.internal.adapter.outbound.persistence.jooq;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopDetailView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopIdView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopOverlapView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopSummaryView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopReader;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopState;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the JOOQ workshop read adapter — full Spring context + real H2 (PostgreSQL mode) + Flyway.
 * Proves the read path assembles {@code *View} projections directly from flat SQL columns (no JPA entity,
 * no domain reconstruction — CQRS bypass). Rows are seeded via {@link WorkshopRepository} (JPA) since this
 * adapter is read-only by design.
 */
@SpringBootTest
class JooqWorkshopReadAdapterTest {

    @Autowired
    private WorkshopReader workshopReader;

    @Autowired
    private WorkshopRepository workshopRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanSchema() {
        jdbcTemplate.update("DELETE FROM workshops");
    }

    private static Workshop newWorkshop() {
        return newWorkshop(Instant.parse("2026-10-01T09:00:00Z"), Instant.parse("2026-10-01T11:00:00Z"));
    }

    private static Workshop newWorkshop(Instant start, Instant end) {
        WorkshopId id = WorkshopId.generate();
        WorkshopTitle title = WorkshopTitle.of("Intro to AI");
        WorkshopDescription description = WorkshopDescription.of("A beginner workshop");
        WorkshopCapacity capacity = WorkshopCapacity.of(30);
        return Workshop.create(id, title, description, start, end,
                start.minus(Duration.ofMinutes(15)), capacity,
                Instant.parse("2026-09-15T00:00:00Z"));
    }

    @Test
    void save_thenFindById_roundTripsThroughDatabase() {
        Workshop workshop = workshopRepository.save(newWorkshop());

        Optional<WorkshopDetailView> found = workshopReader.getById(workshop.id().value());

        assertThat(found).isPresent();
        WorkshopDetailView view = found.get();
        assertThat(view.id()).isEqualTo(workshop.id().value());
        assertThat(view.title()).isEqualTo("Intro to AI");
        assertThat(view.description()).isEqualTo("A beginner workshop");
        assertThat(view.startTime()).isEqualTo(Instant.parse("2026-10-01T09:00:00Z"));
        assertThat(view.endTime()).isEqualTo(Instant.parse("2026-10-01T11:00:00Z"));
        assertThat(view.capacity()).isEqualTo(30);
        assertThat(view.state()).isEqualTo(WorkshopState.DRAFT.name());
        assertThat(view.roomId()).isNull();
        assertThat(view.roomNameSnapshot()).isNull();
        assertThat(view.roomLocationSnapshot()).isNull();
        assertThat(view.isRoomEvicted()).isFalse();
        assertThat(view.roomEvictedAt()).isNull();
        assertThat(view.createdAt()).isNotNull();
        assertThat(view.updatedAt()).isNotNull();
    }

    @Test
    void saveEvictedWorkshop_thenFindById_readsEvictionColumns() {
        Workshop workshop = newWorkshop();
        workshop.plan(RoomReference.of(UUID.randomUUID(), "Room 201", "Floor 2", 50), false,
                workshop.occupancyStart(), Instant.parse("2026-09-15T00:00:01Z"));
        workshop.publish(Instant.parse("2026-09-15T00:00:02Z"), 50);
        workshop.markRoomEvicted(Instant.parse("2026-09-15T00:00:03Z"));
        workshopRepository.save(workshop);

        Optional<WorkshopDetailView> found = workshopReader.getById(workshop.id().value());

        assertThat(found).isPresent();
        assertThat(found.get().isRoomEvicted()).isTrue();
        assertThat(found.get().roomEvictedAt()).isEqualTo(Instant.parse("2026-09-15T00:00:03Z"));
    }

    @Test
    void save_thenFindAll_returnsSummaryProjections() {
        Workshop a = workshopRepository.save(newWorkshop());
        Workshop b = workshopRepository.save(newWorkshop());

        List<WorkshopSummaryView> views = workshopReader.getAll();

        assertThat(views).hasSize(2);
        assertThat(views).anyMatch(v -> v.id().equals(a.id().value()));
        assertThat(views).anyMatch(v -> v.id().equals(b.id().value()));
    }

    @Test
    void getByRoomAndTimeOverlap_returnsOnlyPublishedAndPlanned() {
        UUID roomId = UUID.randomUUID();

        Workshop planned = newWorkshop();
        planned.plan(RoomReference.of(roomId, "Room 201", "Floor 2", 50), false,
                planned.occupancyStart(), Instant.parse("2026-09-15T00:00:01Z"));

        Workshop published = newWorkshop();
        published.plan(RoomReference.of(roomId, "Room 201", "Floor 2", 50), false,
                published.occupancyStart(), Instant.parse("2026-09-15T00:00:01Z"));
        published.publish(Instant.parse("2026-09-15T00:00:02Z"), 50);

        Workshop cancelled = newWorkshop();
        cancelled.plan(RoomReference.of(roomId, "Room 201", "Floor 2", 50), false,
                cancelled.occupancyStart(), Instant.parse("2026-09-15T00:00:01Z"));
        cancelled.publish(Instant.parse("2026-09-15T00:00:02Z"), 50);
        cancelled.cancel(Instant.parse("2026-09-15T00:00:03Z"));

        workshopRepository.saveAll(List.of(planned, published, cancelled));

        Instant overlapStart = Instant.parse("2026-10-01T09:30:00Z");
        Instant overlapEnd = Instant.parse("2026-10-01T10:30:00Z");

        List<WorkshopOverlapView> views = workshopReader.getByRoomAndTimeOverlap(roomId, overlapStart, overlapEnd);

        assertThat(views).hasSize(2);
        assertThat(views).extracting(WorkshopOverlapView::state)
                .containsExactlyInAnyOrder(WorkshopState.PLANNED.name(), WorkshopState.PUBLISHED.name());
        assertThat(views).extracting(WorkshopOverlapView::id)
                .containsExactlyInAnyOrder(planned.id().value(), published.id().value());
    }

    @Test
    void findById_whenAbsent_returnsEmpty() {
        assertThat(workshopReader.getById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findAll_whenEmpty_returnsEmptyList() {
        assertThat(workshopReader.getAll()).isEmpty();
    }

    @Test
    void getPublishedDueToStart_returnsOnlyPublishedWithStartPassed() {
        Workshop due = newWorkshop();
        due.plan(RoomReference.of(UUID.randomUUID(), "Room 201", "Floor 2", 50), false,
                due.occupancyStart(), Instant.parse("2026-09-15T00:00:01Z"));
        due.publish(Instant.parse("2026-09-15T00:00:02Z"), 50);

        Workshop notDue = newWorkshop(Instant.parse("2026-10-01T13:00:00Z"), Instant.parse("2026-10-01T15:00:00Z"));
        notDue.plan(RoomReference.of(UUID.randomUUID(), "Room 201", "Floor 2", 50), false,
                notDue.occupancyStart(), Instant.parse("2026-09-15T00:00:01Z"));
        notDue.publish(Instant.parse("2026-09-15T00:00:02Z"), 50);

        Workshop planned = newWorkshop();
        planned.plan(RoomReference.of(UUID.randomUUID(), "Room 201", "Floor 2", 50), false,
                planned.occupancyStart(), Instant.parse("2026-09-15T00:00:01Z"));

        Workshop inProgress = newWorkshop();
        inProgress.plan(RoomReference.of(UUID.randomUUID(), "Room 201", "Floor 2", 50), false,
                inProgress.occupancyStart(), Instant.parse("2026-09-15T00:00:01Z"));
        inProgress.publish(Instant.parse("2026-09-15T00:00:02Z"), 50);
        inProgress.start(Instant.parse("2026-10-01T09:00:00Z"));

        Workshop completed = newWorkshop();
        completed.plan(RoomReference.of(UUID.randomUUID(), "Room 201", "Floor 2", 50), false,
                completed.occupancyStart(), Instant.parse("2026-09-15T00:00:01Z"));
        completed.publish(Instant.parse("2026-09-15T00:00:02Z"), 50);
        completed.start(Instant.parse("2026-10-01T09:00:00Z"));
        completed.complete(Instant.parse("2026-10-01T11:00:00Z"));

        workshopRepository.saveAll(List.of(due, notDue, planned, inProgress, completed));

        Instant now = Instant.parse("2026-10-01T10:00:00Z");

        List<WorkshopIdView> views = workshopReader.getPublishedDueToStart(now);

        assertThat(views).hasSize(1);
        assertThat(views.getFirst().id()).isEqualTo(due.id().value());
    }

    @Test
    void getInProgressDueToComplete_returnsOnlyInProgressWithEndPassed() {
        Workshop due = newWorkshop();
        due.plan(RoomReference.of(UUID.randomUUID(), "Room 201", "Floor 2", 50), false,
                due.occupancyStart(), Instant.parse("2026-09-15T00:00:01Z"));
        due.publish(Instant.parse("2026-09-15T00:00:02Z"), 50);
        due.start(Instant.parse("2026-10-01T09:00:00Z"));

        Workshop notDue = newWorkshop(Instant.parse("2026-10-01T09:00:00Z"), Instant.parse("2026-10-01T13:00:00Z"));
        notDue.plan(RoomReference.of(UUID.randomUUID(), "Room 201", "Floor 2", 50), false,
                notDue.occupancyStart(), Instant.parse("2026-09-15T00:00:01Z"));
        notDue.publish(Instant.parse("2026-09-15T00:00:02Z"), 50);
        notDue.start(Instant.parse("2026-10-01T09:00:00Z"));

        Workshop completed = newWorkshop();
        completed.plan(RoomReference.of(UUID.randomUUID(), "Room 201", "Floor 2", 50), false,
                completed.occupancyStart(), Instant.parse("2026-09-15T00:00:01Z"));
        completed.publish(Instant.parse("2026-09-15T00:00:02Z"), 50);
        completed.start(Instant.parse("2026-10-01T09:00:00Z"));
        completed.complete(Instant.parse("2026-10-01T11:00:00Z"));

        workshopRepository.saveAll(List.of(due, notDue, completed));

        Instant now = Instant.parse("2026-10-01T11:00:00Z");

        List<WorkshopIdView> views = workshopReader.getInProgressDueToComplete(now);

        assertThat(views).hasSize(1);
        assertThat(views.getFirst().id()).isEqualTo(due.id().value());
    }

    @Test
    void getPublishedOverdueByEndTime_returnsOnlyPublishedOverdue() {
        Workshop overdue = newWorkshop();
        overdue.plan(RoomReference.of(UUID.randomUUID(), "Room 201", "Floor 2", 50), false,
                overdue.occupancyStart(), Instant.parse("2026-09-15T00:00:01Z"));
        overdue.publish(Instant.parse("2026-09-15T00:00:02Z"), 50);

        Workshop notOverdue = newWorkshop(Instant.parse("2026-10-01T09:00:00Z"), Instant.parse("2026-10-01T13:00:00Z"));
        notOverdue.plan(RoomReference.of(UUID.randomUUID(), "Room 201", "Floor 2", 50), false,
                notOverdue.occupancyStart(), Instant.parse("2026-09-15T00:00:01Z"));
        notOverdue.publish(Instant.parse("2026-09-15T00:00:02Z"), 50);

        Workshop overdueInProgress = newWorkshop();
        overdueInProgress.plan(RoomReference.of(UUID.randomUUID(), "Room 201", "Floor 2", 50), false,
                overdueInProgress.occupancyStart(), Instant.parse("2026-09-15T00:00:01Z"));
        overdueInProgress.publish(Instant.parse("2026-09-15T00:00:02Z"), 50);
        overdueInProgress.start(Instant.parse("2026-10-01T09:00:00Z"));

        Workshop overdueCompleted = newWorkshop();
        overdueCompleted.plan(RoomReference.of(UUID.randomUUID(), "Room 201", "Floor 2", 50), false,
                overdueCompleted.occupancyStart(), Instant.parse("2026-09-15T00:00:01Z"));
        overdueCompleted.publish(Instant.parse("2026-09-15T00:00:02Z"), 50);
        overdueCompleted.start(Instant.parse("2026-10-01T09:00:00Z"));
        overdueCompleted.complete(Instant.parse("2026-10-01T11:00:00Z"));

        workshopRepository.saveAll(List.of(overdue, notOverdue, overdueInProgress, overdueCompleted));

        Instant now = Instant.parse("2026-10-01T12:00:00Z");

        List<WorkshopIdView> views = workshopReader.getPublishedOverdueByEndTime(now);

        assertThat(views).hasSize(1);
        assertThat(views.getFirst().id()).isEqualTo(overdue.id().value());
    }
}
