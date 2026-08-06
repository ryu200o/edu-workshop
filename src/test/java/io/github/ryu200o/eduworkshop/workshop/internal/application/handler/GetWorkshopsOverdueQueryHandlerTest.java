package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.GetWorkshopsOverdueQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopSummaryView;
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
class GetWorkshopsOverdueQueryHandlerTest {

    @Mock
    private WorkshopReader workshopReader;

    private GetWorkshopsOverdueQueryHandler handler() {
        return new GetWorkshopsOverdueQueryHandler(workshopReader);
    }

    @Test
    void returnsOverdueViewsFromPort() {
        Instant now = Instant.parse("2026-09-01T12:00:00Z");
        WorkshopSummaryView one = new WorkshopSummaryView(UUID.randomUUID(), "Workshop A", now, now.plusSeconds(3600), false, null, "PUBLISHED");
        when(workshopReader.getPublishedOverdueByEndTime(now)).thenReturn(List.of(one));

        List<WorkshopSummaryView> result = handler().handle(new GetWorkshopsOverdueQuery(now));

        assertThat(result).hasSize(1).containsExactly(one);
    }

    @Test
    void returnsEmptyListWhenNoneOverdue() {
        Instant now = Instant.parse("2026-09-01T12:00:00Z");
        when(workshopReader.getPublishedOverdueByEndTime(now)).thenReturn(List.of());

        assertThat(handler().handle(new GetWorkshopsOverdueQuery(now))).isEmpty();
    }
}