package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.GetWorkshopsQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.view.WorkshopSummaryView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.out.WorkshopReader;
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
class GetWorkshopsQueryHandlerTest {

    @Mock
    private WorkshopReader workshopReader;

    private GetWorkshopsQueryHandler handler() {
        return new GetWorkshopsQueryHandler(workshopReader);
    }

    @Test
    void returnsAllViewsFromPort() {
        Instant now = Instant.now();
        WorkshopSummaryView one = new WorkshopSummaryView(UUID.randomUUID(), "Workshop A", now, now.plusSeconds(3600), "DRAFT");
        WorkshopSummaryView two = new WorkshopSummaryView(UUID.randomUUID(), "Workshop B", now, now.plusSeconds(7200), "SCHEDULED");
        when(workshopReader.findAll()).thenReturn(List.of(one, two));

        List<WorkshopSummaryView> result = handler().handle(new GetWorkshopsQuery());

        assertThat(result).hasSize(2).containsExactly(one, two);
    }

    @Test
    void returnsEmptyListWhenNoWorkshops() {
        when(workshopReader.findAll()).thenReturn(List.of());

        assertThat(handler().handle(new GetWorkshopsQuery())).isEmpty();
    }
}
