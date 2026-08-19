package io.github.ryu200o.eduworkshop.iam.internal.facade;

import io.github.ryu200o.eduworkshop.iam.IamExposeAPI;
import io.github.ryu200o.eduworkshop.iam.contract.UserSummarySnapshot;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserReader;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Module facade implementation (ADR 0010): coordinates directly with the Application read port
 * ({@link UserReader}) — a trusted cross-module collaboration, not an external entry point. Maps the
 * read-side projection onto the public contract snapshot; unknown ids resolve to the standardized
 * fallback (ADR 0020 §1.6).
 */
@Component
class IamExposeAPIImpl implements IamExposeAPI {

    private final UserReader userReader;

    IamExposeAPIImpl(UserReader userReader) {
        this.userReader = userReader;
    }

    @Override
    public UserSummarySnapshot getUserSummary(UUID userId) {
        return userReader.getById(UserId.of(userId))
                .map(view -> new UserSummarySnapshot(
                        view.id(),
                        view.email(),
                        view.fullName(),
                        view.avatarUrl(),
                        view.status()))
                .orElseGet(() -> UserSummarySnapshot.fallback(userId));
    }
}