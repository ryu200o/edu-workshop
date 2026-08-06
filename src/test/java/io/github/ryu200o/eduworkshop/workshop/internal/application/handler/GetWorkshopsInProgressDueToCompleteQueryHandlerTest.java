package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.GetWorkshopsInProgressDueToCompleteQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopIdView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetWorkshopsInProgressDueToCompleteQueryHandlerTest {

    @Mock
    private WorkshopReader workshopReader;

    private GetWorkshopsInProgressDueToCompleteQueryHandler handler() {
        return new GetWorkshopsInProgressDueToCompleteQueryHandler(workshopReader);
    }

    @Test
    void returnsDueViewsFromPort() {
        Instant now = Instant.parse("2026-09-01T12:00:00Z");
        WorkshopIdView one = new WorkshopIdView(UUID.randomUUID());
        when(workshopReader.getInProgressDueToComplete(now)).thenReturn(List.of(one));

        List<WorkshopIdView> result = handler().handle(new GetWorkshopsInProgressDueToCompleteQuery(now));

        assertThat(result).hasSize(1).containsExactly(one);
    }

    @Test
    void returnsEmptyListWhenNoneDue() {
        Instant now = Instant.parse("2026-09-01T12:00:00Z");
        when(workshopReader.getInProgressDueToComplete(now)).thenReturn(List.of());

        assertThat(handler().handle(new GetWorkshopsInProgressDueToCompleteQuery(now))).isEmpty();
    }
}