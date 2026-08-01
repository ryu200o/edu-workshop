package io.github.ryu200o.eduworkshop.registration.internal.application.handler;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationReader;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler;
import io.github.ryu200o.eduworkshop.workshop.contract.CountActiveRegistrationsQuery;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read handler for {@link CountActiveRegistrationsQuery} — the consumer-driven query Workshop
 * dispatches over the shared {@code QueryBus} to learn how many seats are currently active. Resides in
 * the Registration module (which owns the registration data) and keeps the dependency direction
 * natural: Registration stays downstream of Workshop, breaking the Workshop ↔ Registration module cycle.
 * Package-private; side-effect free.
 */
@Component
class CountActiveRegistrationsQueryHandler implements QueryHandler<CountActiveRegistrationsQuery, Integer> {

    private final RegistrationReader registrationReader;

    CountActiveRegistrationsQueryHandler(RegistrationReader registrationReader) {
        this.registrationReader = registrationReader;
    }

    @Override
    @Transactional(readOnly = true)
    public Integer handle(CountActiveRegistrationsQuery query) {
        return registrationReader.countActiveByWorkshop(query.workshopId());
    }
}
