package io.github.ryu200o.eduworkshop.workshop.internal.adapter.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound properties for buffer policy (Spec v2 / ADR 0018 Lean Model — single-sided).
 *
 * <p>Keys: {@code app.workshop.buffer.before-default-minutes} and
 * {@code app.workshop.buffer.max-minutes}. Exposing defaults only; the Lean Model has no
 * after-buffer, so no {@code after-default-minutes} property exists.</p>
 */
@ConfigurationProperties(prefix = "app.workshop.buffer")
record WorkshopBufferProperties(
        int beforeDefaultMinutes,
        int maxMinutes
) {
}