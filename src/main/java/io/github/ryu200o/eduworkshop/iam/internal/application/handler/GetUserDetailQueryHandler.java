package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.GetUserDetailQuery;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.UserDetailView;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserReader;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler;

import org.springframework.stereotype.Component;

/**
 * Read handler for the admin user-detail endpoint ({@code GET /iam/admin/users/{id}}). Unknown id →
 * {@link UserNotFoundException} (HTTP 404).
 */
@Component
class GetUserDetailQueryHandler implements QueryHandler<GetUserDetailQuery, UserDetailView> {

    private final UserReader userReader;

    GetUserDetailQueryHandler(UserReader userReader) {
        this.userReader = userReader;
    }

    @Override
    public UserDetailView handle(GetUserDetailQuery query) {
        UserId userId = UserId.of(query.userId());
        return userReader.getDetail(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}