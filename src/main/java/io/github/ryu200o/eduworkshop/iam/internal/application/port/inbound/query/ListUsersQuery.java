package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.UserSummaryView;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;

import java.util.List;

/**
 * Read-side query for the admin directory ({@code GET /api/v1/iam/admin/users}). Returns every
 * account as a {@link UserSummaryView}, newest first. Admin surface only (HTTP chain rule
 * {@code hasRole("ADMIN")}).
 */
public record ListUsersQuery() implements Query<List<UserSummaryView>> {
}