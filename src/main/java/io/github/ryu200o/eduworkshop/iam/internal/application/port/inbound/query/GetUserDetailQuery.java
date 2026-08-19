package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.UserDetailView;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;

import java.util.UUID;

/**
 * Read-side query for the admin user-detail endpoint ({@code GET /api/v1/iam/admin/users/{id}}).
 * Returns the full account-security profile. Empty lookup surfaces as
 * {@link io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException}
 * (HTTP 404).
 *
 * @param userId the id of the account to inspect
 */
public record GetUserDetailQuery(UUID userId) implements Query<UserDetailView> {
}