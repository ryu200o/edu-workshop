package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.parameter;

/**
 * Operational Policy for the workshop buffer (Spec v2 / ADR 0018 Lean Model — single-sided).
 *
 * <p>Application-layer POJO produced by {@code WorkshopBufferBootstrapConfig} from bound properties
 * and injected into command handlers. The domain never references this type; only the non-negative
 * local invariant lives in {@code WorkshopBuffer}.</p>
 *
 * <p>Lean Model: buffer applies only <em>before</em> {@code start_time}; there is no trailing/after
 * buffer. The max is an Operational Policy — exceeding it is rejected in the Application layer
 * ({@code InvalidBufferSizeException}), not by the domain, so changing the cap is a config-only
 * change that requires no migration.</p>
 */
public record WorkshopBufferParameters(
        int beforeDefaultMinutes,
        int maxMinutes
) {
}
