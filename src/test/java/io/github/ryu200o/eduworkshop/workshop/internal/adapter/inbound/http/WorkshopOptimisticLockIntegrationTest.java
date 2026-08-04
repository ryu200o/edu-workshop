package io.github.ryu200o.eduworkshop.workshop.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Optimistic-lock integration test (ADR 0015 Strategy B): the {@code @Version} column of
 * {@code workshops} is checked at flush time. A transaction that loaded a row, had the row's
 * version bumped by a concurrent commit, and then flushes its update must fail with
 * {@link ObjectOptimisticLockingFailureException}. The Workshop module's HTTP advice maps that to
 * {@code 409 CONFLICT}.
 */
@SpringBootTest
class WorkshopOptimisticLockIntegrationTest {

    @Autowired
    private WorkshopRepository workshopRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private WorkshopId createPersistedWorkshop() {
        return transactionTemplate.execute(status -> {
            Workshop workshop = Workshop.create(
                    WorkshopId.generate(),
                    WorkshopTitle.of("Lock Test"),
                    WorkshopDescription.of("desc"),
                    Instant.parse("2026-09-01T09:00:00Z"),
                    Instant.parse("2026-09-01T11:00:00Z"),
                    WorkshopCapacity.of(20),
                    Instant.now());
            workshopRepository.save(workshop);
            return workshop.id();
        });
    }

    @Test
    void staleVersionUpdate_flushFailsWithObjectOptimisticLockingFailure() {
        WorkshopId id = createPersistedWorkshop();
        UUID idValue = id.value();

        // Simulate a concurrent committed update: bump the row's version outside the write path.
        jdbcTemplate.update("UPDATE workshops SET version = version + 1 WHERE id = ?", idValue.toString());

        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            Workshop workshop = workshopRepository.loadById(id).orElseThrow();
            workshop.updateInformation(
                    WorkshopTitle.of("Stale Title"), WorkshopDescription.of("stale"), 0, Instant.now());
            workshopRepository.save(workshop);
            // Force the version check within this transaction so the test does not depend on
            // optimistic-lock timing at commit.
            jdbcTemplate.update("UPDATE workshops SET version = version + 1 WHERE id = ?", idValue.toString());
            return null;
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void optimisticLockFailure_isMappedTo409ByAdvice() {
        WorkshopExceptionAdvice advice = new WorkshopExceptionAdvice();
        ProblemDetail detail = advice.handleOptimisticLock(new ObjectOptimisticLockingFailureException(
                "WorkshopJpaEntity", WorkshopId.generate().value()));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(detail.getDetail()).contains("Concurrent modification");
    }
}
