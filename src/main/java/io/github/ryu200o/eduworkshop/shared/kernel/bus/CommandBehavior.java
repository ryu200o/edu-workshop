package io.github.ryu200o.eduworkshop.shared.kernel.bus;

import io.github.ryu200o.eduworkshop.shared.cqs.Command;

/**
 * A single cross-cutting concern in the command dispatch pipeline (e.g. logging, validation,
 * authorization, metrics, transaction). Behaviors form a Chain of Responsibility that terminates in the
 * {@link io.github.ryu200o.eduworkshop.shared.cqs.CommandHandler}.
 *
 * <p>Implementations must invoke {@link BehaviorChain#next(Object)} exactly once to continue the chain
 * (or short-circuit by not calling it).</p>
 */
@FunctionalInterface
public interface CommandBehavior {

    Object handle(Command<?> command, BehaviorChain next);
}
