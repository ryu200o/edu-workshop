package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CreateWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.parameter.WorkshopBufferParameters;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.parameter.WorkshopCheckInParameters;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopDomainException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class CreateWorkshopCommandHandlerTest {

    @Mock
    private WorkshopRepository workshopRepository;

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-07-22T10:00:00Z"), ZoneOffset.UTC);
    }

    private CreateWorkshopCommandHandler handler() {
        return new CreateWorkshopCommandHandler(workshopRepository, new WorkshopBufferParameters(15),
                new WorkshopCheckInParameters(15), clock);
    }

    @Test
    void happyPath_createsAndPersistsWorkshop() {
        var command = new CreateWorkshopCommand(UUID.randomUUID(), 
                "Spring Boot Workshop",
                "Hands-on intro to Spring Modulith.",
                Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-01T11:00:00Z"),
                30);

        when(workshopRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(command);

        ArgumentCaptor<Workshop> savedCaptor = ArgumentCaptor.forClass(Workshop.class);
        verify(workshopRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().id().value()).isNotNull();
        assertThat(savedCaptor.getValue().title().value()).isEqualTo("Spring Boot Workshop");
    }

    @Test
    void ramGuard_rejectsBlankTitle() {
        var command = new CreateWorkshopCommand(UUID.randomUUID(), 
                "   ",
                "desc",
                Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-01T11:00:00Z"),
                30);

        assertThatThrownBy(() -> handler().handle(command))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(workshopRepository);
    }

    @Test
    void ramGuard_rejectsEndNotAfterStart() {
        var command = new CreateWorkshopCommand(UUID.randomUUID(), 
                "Workshop",
                "desc",
                Instant.parse("2026-09-01T11:00:00Z"),
                Instant.parse("2026-09-01T09:00:00Z"),
                30);

        assertThatThrownBy(() -> handler().handle(command))
                .isInstanceOf(WorkshopDomainException.class)
                .hasMessageContaining("after startTime");

        verifyNoInteractions(workshopRepository);
    }

    @Test
    void ramGuard_rejectsNonPositiveCapacity() {
        var command = new CreateWorkshopCommand(UUID.randomUUID(), 
                "Workshop",
                "desc",
                Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-01T11:00:00Z"),
                0);

        assertThatThrownBy(() -> handler().handle(command))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(workshopRepository);
    }
}
