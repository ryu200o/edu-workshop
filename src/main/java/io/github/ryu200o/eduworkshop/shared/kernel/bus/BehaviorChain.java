package io.github.ryu200o.eduworkshop.shared.kernel.bus;

import io.github.ryu200o.eduworkshop.shared.cqs.Command;

/**
 * Continuation of a {@link CommandBehavior} chain. Wrapping a command and the remaining chain, {@link #next}
 * advances to the next behavior (or the terminal handler when the chain is exhausted).
 */
public interface BehaviorChain {

    Object next(Command<?> command);
}
