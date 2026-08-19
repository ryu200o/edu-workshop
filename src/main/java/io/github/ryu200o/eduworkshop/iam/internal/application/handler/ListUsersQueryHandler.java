package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.ListUsersQuery;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.UserSummaryView;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserReader;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Read handler for the admin user directory ({@code GET /iam/admin/users}). Returns every account as
 * a summary projection, newest first. Admin surface only (HTTP chain rule {@code hasRole("ADMIN")}).
 */
@Component
class ListUsersQueryHandler implements QueryHandler<ListUsersQuery, List<UserSummaryView>> {

    private final UserReader userReader;

    ListUsersQueryHandler(UserReader userReader) {
        this.userReader = userReader;
    }

    @Override
    public List<UserSummaryView> handle(ListUsersQuery query) {
        return userReader.list();
    }
}