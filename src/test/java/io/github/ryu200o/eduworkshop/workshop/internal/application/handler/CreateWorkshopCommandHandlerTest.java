package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.command.CreateWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.out.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopDomainException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        return new CreateWorkshopCommandHandler(workshopRepository, clock);
    }

    @Test
    void happyPath_createsAndPersistsWorkshop() {
        var command = new CreateWorkshopCommand(
                "Spring Boot Workshop",
                "Hands-on intro to Spring Modulith.",
                Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-01T11:00:00Z"),
                30);

        when(workshopRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateWorkshopCommand.Result result = handler().handle(command);

        assertThat(result.id()).isNotNull();
        assertThat(result.title()).isEqualTo("Spring Boot Workshop");

        verify(workshopRepository).save(any());
    }

    @Test
    void ramGuard_rejectsBlankTitle() {
        var command = new CreateWorkshopCommand(
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
        var command = new CreateWorkshopCommand(
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
        var command = new CreateWorkshopCommand(
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
