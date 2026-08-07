package io.github.ryu200o.eduworkshop.workshop.internal.adapter.inbound.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operational Policy for workshop buffer time (Spec v2 / ADR 0018 P2). Bound from
 * {@code app.workshop.buffer.*} in {@code application.properties}. Deliberately lives at the Application
 * layer — the domain {@link io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopBuffer}
 * never hardcodes the upper bound.
 */
@ConfigurationProperties(prefix = "app.workshop.buffer")
public record WorkshopBufferConfig(
        int beforeDefaultMinutes,
        int afterDefaultMinutes,
        int minMinutes,
        int maxMinutes
) {
}
