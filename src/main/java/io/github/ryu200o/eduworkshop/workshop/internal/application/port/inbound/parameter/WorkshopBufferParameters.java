package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.parameter;

/**
 * Operational Policy for the workshop buffer (Spec v3 / ADR 0018 — System Buffer Guardrail, single knob).
 *
 * <p>Application-layer POJO produced by {@code WorkshopBufferBootstrapConfig} from bound properties and injected into
 * command handlers. The domain never references this type; the only local invariant lives in
 * {@code WorkshopBuffer} ({@code beforeMinutes >= 0}).</p>
 *
 * <p>Single-sided: buffer applies only <em>before</em> {@code start_time}; there is no trailing/after buffer.
 * The buffer value is snapshot at scheduling from this default — callers must not pass a custom buffer.
 * There is no max/cap knob: the storage ceiling is enforced by a DB {@code CHECK (buffer_before_minutes BETWEEN 0 AND 300)}
 * and doubles as the superset bound for overlap checks, so changing the default is a config-only change.</p>
 */
public record WorkshopBufferParameters(
        int beforeDefaultMinutes
) {
}