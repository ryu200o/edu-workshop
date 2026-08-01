package io.github.ryu200o.eduworkshop.registration.internal.facade;

import io.github.ryu200o.eduworkshop.registration.RegistrationExposeAPI;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationReader;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Package-private implementation of {@link RegistrationExposeAPI} — the Module Facade for
 * Registration. Resides inside the information-hiding boundary (internal/facade/). Coordinates
 * directly with application ports — no Command/Query Bus involved (per ADR 0010).
 */
@Component
class RegistrationExposeAPIImpl implements RegistrationExposeAPI {

    private final RegistrationReader registrationReader;

    RegistrationExposeAPIImpl(RegistrationReader registrationReader) {
        this.registrationReader = registrationReader;
    }

    @Override
    public int countActiveRegistrations(UUID workshopId) {
        return registrationReader.countActiveByWorkshop(workshopId);
    }
}
