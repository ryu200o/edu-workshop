package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.parameter;

/**
 * Operational Policy for workshop buffer time (Spec v2 / ADR 0018 P2). Application-layer POJO produced
 * by {@code WorkshopBufferBootstrapConfig} from bound properties — injected into command handlers.
 * The domain never references this type; only the non-negative local invariant lives in
 * {@link io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopBuffer}.
 */
public record WorkshopBufferParameters(
        int beforeDefaultMinutes,
        int afterDefaultMinutes,
        int minMinutes,
        int maxMinutes
) {
}
