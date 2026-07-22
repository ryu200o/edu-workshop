package io.github.ryu200o.eduworkshop.workshop.internal.adapter.driven.persistence.jpa;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopState;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class JpaWorkshopWriteAdapterTest {

    @Autowired
    private JpaWorkshopWriteAdapter adapter;

    @Test
    void saveAndLoadById_roundTrip() {
        Instant now = Instant.now();
        WorkshopId id = WorkshopId.generate();
        WorkshopTitle title = WorkshopTitle.of("Test Workshop");
        WorkshopDescription description = WorkshopDescription.of("Test description");
        Instant start = Instant.parse("2026-09-01T09:00:00Z");
        Instant end = Instant.parse("2026-09-01T11:00:00Z");
        WorkshopCapacity capacity = WorkshopCapacity.of(25);

        Workshop saved = adapter.save(
                Workshop.create(id, title, description, start, end, capacity, now));

        assertThat(saved.id()).isEqualTo(id);
        assertThat(saved.state()).isEqualTo(WorkshopState.DRAFT);

        Workshop loaded = adapter.loadById(id).orElseThrow();
        assertThat(loaded.id()).isEqualTo(id);
        assertThat(loaded.title()).isEqualTo(title);
        assertThat(loaded.description()).isEqualTo(description);
        assertThat(loaded.startTime()).isEqualTo(start);
        assertThat(loaded.endTime()).isEqualTo(end);
        assertThat(loaded.capacity()).isEqualTo(capacity);
        assertThat(loaded.state()).isEqualTo(WorkshopState.DRAFT);
        assertThat(loaded.roomReference()).isNull();
        assertThat(loaded.createdAt()).isNotNull();
        assertThat(loaded.updatedAt()).isNotNull();
    }

    @Test
    void loadById_absent_returnsEmpty() {
        assertThat(adapter.loadById(WorkshopId.generate())).isEmpty();
    }
}
