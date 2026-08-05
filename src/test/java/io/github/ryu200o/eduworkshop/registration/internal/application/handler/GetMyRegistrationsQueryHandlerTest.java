package io.github.ryu200o.eduworkshop.registration.internal.application.handler;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.GetMyRegistrationsQuery;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.MyRegistrationStatus;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.view.MyRegistrationView;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationReader;

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
class GetMyRegistrationsQueryHandlerTest {

    @Mock
    private RegistrationReader registrationReader;

    private GetMyRegistrationsQueryHandler handler() {
        return new GetMyRegistrationsQueryHandler(registrationReader);
    }

    private static MyRegistrationView view(UUID registrationId, MyRegistrationStatus status) {
        return new MyRegistrationView(registrationId, UUID.randomUUID(), "Docker Essentials",
                Instant.parse("2026-09-01T09:00:00Z"), Instant.parse("2026-09-01T11:00:00Z"), "A-101",
                UUID.randomUUID(), status, Instant.parse("2026-08-01T10:00:00Z"), null);
    }

    @Test
    void filtersByStatus_pushesDownToReader() {
        UUID userId = UUID.randomUUID();
        List<MyRegistrationView> expected = List.of(view(UUID.randomUUID(), MyRegistrationStatus.REGISTERED));
        when(registrationReader.getByUserId(userId, MyRegistrationStatus.REGISTERED)).thenReturn(expected);

        List<MyRegistrationView> result = handler().handle(new GetMyRegistrationsQuery(userId, MyRegistrationStatus.REGISTERED));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void nullStatus_returnsFullHistory() {
        UUID userId = UUID.randomUUID();
        List<MyRegistrationView> expected = List.of(
                view(UUID.randomUUID(), MyRegistrationStatus.REGISTERED),
                view(UUID.randomUUID(), MyRegistrationStatus.REFUNDED));
        when(registrationReader.getByUserId(userId, null)).thenReturn(expected);

        List<MyRegistrationView> result = handler().handle(new GetMyRegistrationsQuery(userId, null));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void learnerWithoutBookings_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        when(registrationReader.getByUserId(userId, null)).thenReturn(List.of());

        List<MyRegistrationView> result = handler().handle(new GetMyRegistrationsQuery(userId, null));

        assertThat(result).isEmpty();
    }
}