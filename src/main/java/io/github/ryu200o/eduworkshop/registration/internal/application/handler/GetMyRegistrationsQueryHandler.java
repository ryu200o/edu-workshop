package io.github.ryu200o.eduworkshop.registration.internal.application.handler;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.GetMyRegistrationsQuery;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.view.MyRegistrationView;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationReader;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read handler for the learner "My Bookings" query.
 *
 * <p>Pure read orchestration (CQRS): delegates to {@link RegistrationReader#getByUserId}, which
 * pushes the whole query down to a single SELECT on the {@code registrations} table (ADR 0007
 * selective snapshots — no cross-module JOIN, no in-memory filtering). A learner with no bookings
 * simply gets an empty list (HTTP 200, not an error).</p>
 */
@Component
class GetMyRegistrationsQueryHandler implements QueryHandler<GetMyRegistrationsQuery, List<MyRegistrationView>> {

    private final RegistrationReader registrationReader;

    GetMyRegistrationsQueryHandler(RegistrationReader registrationReader) {
        this.registrationReader = registrationReader;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MyRegistrationView> handle(GetMyRegistrationsQuery query) {
        return registrationReader.getByUserId(query.userId(), query.status());
    }
}
