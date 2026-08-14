package io.github.ryu200o.eduworkshop.workshop.internal.facade;

import io.github.ryu200o.eduworkshop.workshop.WorkshopExposeAPI;
import io.github.ryu200o.eduworkshop.workshop.contract.AttendanceStatusContract;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.parameter.WorkshopCheckInParameters;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopDetailView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopReader;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Contract boundary test for {@link WorkshopExposeAPI#evaluateCheckIn} (Epic 3B) — the Workshop
 * module owns the attendance policy (ADR 0019 §13.1) and decides {@code ATTENDED | LATE} from its
 * own operational setting {@code app.workshop.checkin.late-after-minutes} (OQ-3B-5).
 */
@ExtendWith(MockitoExtension.class)
class WorkshopExposeAPIImplEvaluateCheckInTest {

    @Mock
    private WorkshopReader workshopReader;

    @Mock
    private WorkshopRepository workshopRepository;

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final Instant START_TIME = Instant.parse("2026-09-01T09:00:00Z");
    private static final int LATE_AFTER_MINUTES = 15;
    private static final Instant LATE_THRESHOLD = START_TIME.plusSeconds(LATE_AFTER_MINUTES * 60L);

    private WorkshopExposeAPI facade;

    @BeforeEach
    void setUp() {
        facade = new WorkshopExposeAPIImpl(workshopReader, workshopRepository,
                new WorkshopCheckInParameters(LATE_AFTER_MINUTES));
    }

    private void stubWorkshop() {
        when(workshopReader.getById(WORKSHOP_ID)).thenReturn(Optional.of(new WorkshopDetailView(
                WORKSHOP_ID, "WS", "desc", null, null, null, null, false, false, null,
                START_TIME, START_TIME.plusSeconds(3600), 30, "IN_PROGRESS",
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T08:59:00Z"))));
    }

    @Test
    void checkedInExactlyAtThreshold_isAttended() {
        stubWorkshop();

        assertThat(facade.evaluateCheckIn(WORKSHOP_ID, LATE_THRESHOLD))
                .contains(AttendanceStatusContract.ATTENDED);
    }

    @Test
    void checkedInBeforeThreshold_isAttended() {
        stubWorkshop();

        assertThat(facade.evaluateCheckIn(WORKSHOP_ID, LATE_THRESHOLD.minusSeconds(1)))
                .contains(AttendanceStatusContract.ATTENDED);
    }

    @Test
    void checkedInAfterThreshold_isLate() {
        stubWorkshop();

        assertThat(facade.evaluateCheckIn(WORKSHOP_ID, LATE_THRESHOLD.plusSeconds(1)))
                .contains(AttendanceStatusContract.LATE);
    }

    @Test
    void workshopNotFound_returnsEmpty() {
        when(workshopReader.getById(WORKSHOP_ID)).thenReturn(Optional.empty());

        assertThat(facade.evaluateCheckIn(WORKSHOP_ID, Instant.now())).isEmpty();
    }
}
