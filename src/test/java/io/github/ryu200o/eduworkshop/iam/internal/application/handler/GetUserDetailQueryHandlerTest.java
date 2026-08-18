package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.GetUserDetailQuery;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.UserDetailView;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserReader;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserDetailQueryHandlerTest {

    @Mock
    private UserReader userReader;

    private GetUserDetailQueryHandler handler() {
        return new GetUserDetailQueryHandler(userReader);
    }

    @Test
    void getDetail_existingUser_returnsFullSecurityProfile() {
        UUID userId = UUID.randomUUID();
        UserDetailView view = new UserDetailView(userId, "student@example.com", "Nguyen Van A", null,
                null, null, "LOCKED", Set.of("USER"), false,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-18T09:00:00Z"),
                4, 1, Instant.parse("2026-08-18T09:15:00Z"), Instant.parse("2026-08-18T09:00:00Z"));
        when(userReader.getDetail(UserId.of(userId))).thenReturn(Optional.of(view));

        UserDetailView result = handler().handle(new GetUserDetailQuery(userId));

        assertThat(result).isEqualTo(view);
    }

    @Test
    void getDetail_unknownUser_throwsNotFound() {
        when(userReader.getDetail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new GetUserDetailQuery(UUID.randomUUID())))
                .isInstanceOf(UserNotFoundException.class);
    }
}