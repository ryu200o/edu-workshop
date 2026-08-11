package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.parameter;

/**
 * Operational Policy for the workshop buffer (Spec v3 / ADR 0018 — System Buffer Guardrail, single knob).
 *
 * <p>Application-layer POJO produced by {@code WorkshopBufferBootstrapConfig} from bound properties
 * and injected into command handlers. It feeds the ADR 0018 pure function applied at the Application
 * edge: {@code occupancy_start = startTime − beforeDefaultMinutes}. The domain never references this
 * type and never receives the buffer minutes — it only receives the already-computed
 * {@code occupancyStart}: the buffer is consumed at the Application boundary, never persisted, never
 * hinted into the Aggregate (ADR 0018 §4.1). Negative values are rejected here (config validation,
 * {@code WorkshopBufferBootstrapConfig}).</p>
 *
 * <p>Single-sided: buffer applies only <em>before</em> {@code start_time}; there is no trailing/after
 * buffer. The buffer value is applied at scheduling from this default — callers must not pass a custom
 * buffer. There is no max/cap knob and no {@code STORAGE_CEILING}: only the single operational default
 * exists, so changing the default is a config-only change.</p>
 */
public record WorkshopBufferParameters(
        int beforeDefaultMinutes
) {
}