package io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.view.MyRegistrationView;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;

import java.util.List;
import java.util.UUID;

/**
 * Query for the learner "My Bookings" read side.
 *
 * <p>The {@code status} filter is optional and user-selectable from {@code REGISTERED} /
 * {@code CANCELLED} only (Epic 2 sign-off Q4): when {@code null} the full booking history is
 * returned, including system-issued {@code REFUNDED} rows.</p>
 *
 * @param userId the acting learner (from the {@code AuthenticatedPrincipal})
 * @param status optional status filter; {@code null} means "no filter — full history"
 */
public record GetMyRegistrationsQuery(UUID userId, MyRegistrationStatus status)
        implements Query<List<MyRegistrationView>> {
}
