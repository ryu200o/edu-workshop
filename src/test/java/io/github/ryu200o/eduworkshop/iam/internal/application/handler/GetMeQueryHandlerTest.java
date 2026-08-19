package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.GetMeQuery;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.MeView;
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
class GetMeQueryHandlerTest {

    @Mock
    private UserReader userReader;

    private GetMeQueryHandler handler() {
        return new GetMeQueryHandler(userReader);
    }

    @Test
    void getMe_existingUser_returnsSelfProfile() {
        UUID userId = UUID.randomUUID();
        MeView view = new MeView(userId, "student@example.com", "Nguyen Van A", "0901234567",
                "B21DCVT000", null, "ACTIVE", Set.of("USER"), false, Instant.parse("2026-08-01T00:00:00Z"));
        when(userReader.getMe(UserId.of(userId))).thenReturn(Optional.of(view));

        MeView result = handler().handle(new GetMeQuery(userId));

        assertThat(result).isEqualTo(view);
    }

    @Test
    void getMe_unknownUser_throwsNotFound() {
        when(userReader.getMe(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new GetMeQuery(UUID.randomUUID())))
                .isInstanceOf(UserNotFoundException.class);
    }
}