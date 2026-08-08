package io.github.ryu200o.eduworkshop.workshop.internal.adapter.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound properties for buffer policy (Spec v3 / ADR 0018 — System Buffer Guardrail, single knob).
 *
 * <p>Only key: {@code app.workshop.buffer.before-default-minutes}. The Lean/System Guardrail model has a single-sided
 * buffer, no after-buffer, no max/cap knob and no custom buffer at create — the storage ceiling is a DB
 * {@code CHECK} constraint, not a property. Therefore no {@code max-minutes} property exists.</p>
 */
@ConfigurationProperties(prefix = "app.workshop.buffer")
record WorkshopBufferProperties(
        int beforeDefaultMinutes
) {
}