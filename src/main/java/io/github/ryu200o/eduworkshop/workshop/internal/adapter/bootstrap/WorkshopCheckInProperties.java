package io.github.ryu200o.eduworkshop.workshop.internal.adapter.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound properties for the QR self check-in policy (Epic 3B, OQ-3B-5) — a Workshop-side operational
 * setting owned by the Workshop module (ADR 0019 §13.1), mirroring {@link WorkshopBufferProperties}.
 *
 * <p>Only key: {@code app.workshop.checkin.late-after-minutes} (default 15). A learner who checks in
 * no later than {@code startTime + lateAfterMinutes} is {@code ATTENDED}, otherwise {@code LATE}.
 * The value is a pure operational knob: it never enters the Aggregate and never persists.</p>
 */
@ConfigurationProperties(prefix = "app.workshop.checkin")
record WorkshopCheckInProperties(
        int lateAfterMinutes
) {
}
