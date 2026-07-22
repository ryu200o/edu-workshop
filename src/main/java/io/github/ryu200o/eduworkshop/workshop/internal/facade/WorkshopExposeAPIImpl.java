package io.github.ryu200o.eduworkshop.workshop.internal.facade;

import io.github.ryu200o.eduworkshop.workshop.WorkshopExposeAPI;
import org.springframework.stereotype.Component;

/**
 * Package-private implementation of {@link WorkshopExposeAPI} — the Module Facade for Workshop.
 * Resides inside the information-hiding boundary (internal/facade/). Coordinates directly
 * with application ports — no Command/Query Bus involved (per ADR 0010).
 */
@Component
class WorkshopExposeAPIImpl implements WorkshopExposeAPI {
}
