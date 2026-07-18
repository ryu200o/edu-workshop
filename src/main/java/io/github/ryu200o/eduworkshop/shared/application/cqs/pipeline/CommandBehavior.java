package io.github.ryu200o.eduworkshop.shared.application.cqs.pipeline;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

/**
 * A single cross-cutting concern in the command dispatch pipeline (e.g. logging, validation,
 * authorization, metrics, transaction). Behaviors form a Chain of Responsibility that terminates in the
 * {@link io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler}.
 *
 * <p>Implementations must invoke {@link BehaviorChain#next(Object)} exactly once to continue the chain
 * (or short-circuit by not calling it).</p>
 */
@FunctionalInterface
public interface CommandBehavior {

    Object handle(Command<?> command, BehaviorChain next);
}
