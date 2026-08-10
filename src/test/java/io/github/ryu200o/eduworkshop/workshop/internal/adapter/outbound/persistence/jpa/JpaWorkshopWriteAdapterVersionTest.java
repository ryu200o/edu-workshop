package io.github.ryu200o.eduworkshop.workshop.internal.adapter.outbound.persistence.jpa;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Optimistic-lock round-trip for the Workshop write adapter (ADR 0015 Strategy B). Runs in real
 * transactions (no {@code @Transactional} on the class) so each {@code save()} commits and the
 * {@code @Version} increment becomes observable — Workshop uses the plain {@code save()} path
 * (Rule 2), so the increment is only visible after commit.
 */
@SpringBootTest
class JpaWorkshopWriteAdapterVersionTest {

    @Autowired
    private WorkshopRepository workshopRepository;

    @Autowired
    private WorkshopJpaRepository workshopJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void save_thenUpdate_keepsAndIncrementsOptimisticVersion() {
        WorkshopId id = transactionTemplate.execute(status -> {
            Instant start = Instant.parse("2026-09-01T09:00:00Z");
            Workshop workshop = Workshop.create(
                    WorkshopId.generate(),
                    WorkshopTitle.of("Test Workshop"),
                    WorkshopDescription.of("Test description"),
                    start,
                    Instant.parse("2026-09-01T11:00:00Z"),
                    start.minus(Duration.ofMinutes(15)),
                    WorkshopCapacity.of(25),
                    Instant.now());
            workshopRepository.save(workshop);
            return workshop.id();
        });

        Long versionAfterInsert = workshopJpaRepository.findById(id.value()).orElseThrow().getVersion();

        transactionTemplate.execute(status -> {
            Workshop workshop = workshopRepository.loadById(id).orElseThrow();
            workshop.updateInformation(
                    WorkshopTitle.of("Renamed"), WorkshopDescription.of("Changed"), 0, Instant.now());
            workshopRepository.save(workshop);
            return null;
        });

        assertThat(workshopJpaRepository.findById(id.value()).orElseThrow().getVersion())
                .isEqualTo(versionAfterInsert + 1L);
    }
}
