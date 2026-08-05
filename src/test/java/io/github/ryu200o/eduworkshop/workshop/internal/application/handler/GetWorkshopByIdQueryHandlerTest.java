package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.GetWorkshopByIdQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopDetailView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetWorkshopByIdQueryHandlerTest {

    @Mock
    private WorkshopReader workshopReader;

    private GetWorkshopByIdQueryHandler handler() {
        return new GetWorkshopByIdQueryHandler(workshopReader);
    }

    @Test
    void happyPath_returnsProjectionFromPort() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        WorkshopDetailView expected = new WorkshopDetailView(
                id, "Test Title", "A description",
                UUID.randomUUID(), "F-201", "F/2", 50, false, false, null,
                now, now.plusSeconds(7200), 25, "PLANNED", now, now);
        when(workshopReader.getById(id)).thenReturn(Optional.of(expected));

        WorkshopDetailView result = handler().handle(new GetWorkshopByIdQuery(id));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void notFound_throwsWorkshopNotFoundException() {
        UUID id = UUID.randomUUID();
        when(workshopReader.getById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new GetWorkshopByIdQuery(id)))
                .isInstanceOf(WorkshopNotFoundException.class);
    }
}
