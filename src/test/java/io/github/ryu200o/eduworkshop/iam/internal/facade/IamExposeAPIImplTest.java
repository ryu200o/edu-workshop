package io.github.ryu200o.eduworkshop.iam.internal.facade;

import io.github.ryu200o.eduworkshop.iam.IamExposeAPI;
import io.github.ryu200o.eduworkshop.iam.contract.UserSummarySnapshot;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.UserSummaryView;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserReader;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the IAM module facade ({@link IamExposeAPI} impl). Proves the facade maps the read
 * projection ({@link UserReader#getById}) onto the public contract snapshot and falls back to the
 * standardized {@link UserSummarySnapshot#fallback} for unknown ids (ADR 0020 §1.6).
 */
@ExtendWith(MockitoExtension.class)
class IamExposeAPIImplTest {

    @Mock
    private UserReader userReader;

    private IamExposeAPI exposeApi() {
        return new IamExposeAPIImpl(userReader);
    }

    @Test
    void getUserSummary_existingUser_mapsReaderViewToSnapshot() {
        UUID userId = UUID.randomUUID();
        UserSummaryView view = new UserSummaryView(
                userId, "student@example.com", "Nguyen Van A", "https://cdn/avatar.png",
                "ACTIVE", Set.of("USER"));
        when(userReader.getById(UserId.of(userId))).thenReturn(Optional.of(view));

        UserSummarySnapshot snapshot = exposeApi().getUserSummary(userId);

        assertThat(snapshot.userId()).isEqualTo(userId);
        assertThat(snapshot.email()).isEqualTo("student@example.com");
        assertThat(snapshot.fullName()).isEqualTo("Nguyen Van A");
        assertThat(snapshot.avatarUrl()).isEqualTo("https://cdn/avatar.png");
        assertThat(snapshot.status()).isEqualTo("ACTIVE");
        verify(userReader).getById(UserId.of(userId));
    }

    @Test
    void getUserSummary_unknownUser_returnsStandardizedFallback() {
        UUID userId = UUID.randomUUID();
        when(userReader.getById(UserId.of(userId))).thenReturn(Optional.empty());

        UserSummarySnapshot snapshot = exposeApi().getUserSummary(userId);

        assertThat(snapshot.userId()).isEqualTo(userId);
        assertThat(snapshot.email()).isEqualTo("N/A");
        assertThat(snapshot.fullName()).isEqualTo("Người dùng cũ");
        assertThat(snapshot.avatarUrl()).isNull();
        assertThat(snapshot.status()).isEqualTo("UNKNOWN");
        verify(userReader).getById(UserId.of(userId));
    }
}