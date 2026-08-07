package io.github.ryu200o.eduworkshop.workshop.internal.adapter.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code app.workshop.buffer.*} Operational Policy from {@code application.properties}
 * (Spec v2 / ADR 0018 P2). Naming follows the adapter-inbound-bootstrap convention (cf.
 * {@code WorkshopSchedulingConfig}) — a properties class, not a handler config.
 */
@ConfigurationProperties(prefix = "app.workshop.buffer")
record WorkshopBufferProperties(
        int beforeDefaultMinutes,
        int afterDefaultMinutes,
        int minMinutes,
        int maxMinutes
) {}
