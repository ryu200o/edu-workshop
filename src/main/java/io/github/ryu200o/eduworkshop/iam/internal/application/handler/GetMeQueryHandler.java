package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.GetMeQuery;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.MeView;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserReader;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler;

import org.springframework.stereotype.Component;

/**
 * Read handler for the authenticated caller's own profile ({@code GET /iam/me}). The caller id comes
 * from the JWT principal, so this endpoint can never be used to read a different account. Side-effect
 * free; goes through the read port {@link UserReader} (CQRS bypass, ADR 0017).
 */
@Component
class GetMeQueryHandler implements QueryHandler<GetMeQuery, MeView> {

    private final UserReader userReader;

    GetMeQueryHandler(UserReader userReader) {
        this.userReader = userReader;
    }

    @Override
    public MeView handle(GetMeQuery query) {
        return userReader.getMe(UserId.of(query.userId()))
                .orElseThrow(() -> new UserNotFoundException(UserId.of(query.userId())));
    }
}