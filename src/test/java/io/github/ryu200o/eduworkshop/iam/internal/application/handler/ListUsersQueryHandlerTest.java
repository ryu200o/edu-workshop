package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.ListUsersQuery;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.UserSummaryView;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserReader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListUsersQueryHandlerTest {

    @Mock
    private UserReader userReader;

    private ListUsersQueryHandler handler() {
        return new ListUsersQueryHandler(userReader);
    }

    @Test
    void list_returnsAllSummariesFromReadSide() {
        UserSummaryView a = new UserSummaryView(UUID.randomUUID(), "a@example.com", "A", null,
                "ACTIVE", Set.of("USER"));
        UserSummaryView b = new UserSummaryView(UUID.randomUUID(), "b@example.com", "B", null,
                "LOCKED", Set.of("USER", "ADMIN"));
        when(userReader.list()).thenReturn(List.of(b, a));

        List<UserSummaryView> result = handler().handle(new ListUsersQuery());

        assertThat(result).containsExactly(b, a);
    }
}