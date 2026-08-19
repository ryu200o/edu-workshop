package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.MeView;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;

import java.util.UUID;

/**
 * Read-side query for the authenticated caller's own profile ({@code GET /api/v1/iam/me}). The
 * {@code userId} comes from the JWT {@link io.github.ryu200o.eduworkshop.shared.security.AuthenticatedPrincipal}
 * — the caller can never look up somebody else's profile through this endpoint.
 *
 * @param userId the id of the authenticated caller
 */
public record GetMeQuery(UUID userId) implements Query<MeView> {
}