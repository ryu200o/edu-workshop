package io.github.ryu200o.eduworkshop.workshop.internal.adapter.driven.persistence.jooq;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.view.WorkshopDetailView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.view.WorkshopSummaryView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.out.WorkshopReader;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.out.WorkshopRepository;
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
        WorkshopId id = WorkshopId.generate();
        WorkshopTitle title = WorkshopTitle.of("Intro to AI");
        WorkshopDescription description = WorkshopDescription.of("A beginner workshop");
        Instant start = Instant.parse("2026-10-01T09:00:00Z");
        Instant end = Instant.parse("2026-10-01T11:00:00Z");
        WorkshopCapacity capacity = WorkshopCapacity.of(30);
        return Workshop.create(id, title, description, start, end, capacity, Instant.parse("2026-09-15T00:00:00Z"));
    }

    @Test
    void save_thenFindById_roundTripsThroughDatabase() {
        Workshop workshop = workshopRepository.save(newWorkshop());

        Optional<WorkshopDetailView> found = workshopReader.findById(workshop.id().value());

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
        assertThat(view.createdAt()).isNotNull();
        assertThat(view.updatedAt()).isNotNull();
    }

    @Test
    void save_thenFindAll_returnsSummaryProjections() {
        Workshop a = workshopRepository.save(newWorkshop());
        Workshop b = workshopRepository.save(newWorkshop());

        List<WorkshopSummaryView> views = workshopReader.findAll();

        assertThat(views).hasSize(2);
        assertThat(views).anyMatch(v -> v.id().equals(a.id().value()));
        assertThat(views).anyMatch(v -> v.id().equals(b.id().value()));
    }

    @Test
    void findById_whenAbsent_returnsEmpty() {
        assertThat(workshopReader.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findAll_whenEmpty_returnsEmptyList() {
        assertThat(workshopReader.findAll()).isEmpty();
    }
}
