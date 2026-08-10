package io.github.ryu200o.eduworkshop.workshop.internal.adapter.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound properties for buffer policy (Spec v3 / ADR 0018 — System Buffer Guardrail, single knob).
 *
 * <p>Only key: {@code app.workshop.buffer.before-default-minutes}. The Lean/System Guardrail model has
 * a single-sided buffer (before {@code start_time} only), no after-buffer, no max/cap knob and no
 * custom buffer at create — only this operational default drives the ADR 0018 pure function
 * {@code occupancy_start = startTime − beforeDefaultMinutes}. The buffer is never persisted and never
 * enters the Aggregate (ADR 0018 §4.1).</p>
 */
@ConfigurationProperties(prefix = "app.workshop.buffer")
record WorkshopBufferProperties(
        int beforeDefaultMinutes
) {
}