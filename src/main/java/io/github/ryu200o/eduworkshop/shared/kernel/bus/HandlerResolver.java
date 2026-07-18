package io.github.ryu200o.eduworkshop.shared.kernel.bus;

import io.github.ryu200o.eduworkshop.shared.cqs.Command;
import io.github.ryu200o.eduworkshop.shared.cqs.CommandHandler;

/**
 * Resolves a {@link CommandHandler} for a concrete {@link Command} type. Pure lookup: it knows nothing
 * about Spring, the dispatch pipeline, or policy. The mapping is sourced from the immutable
 * {@link HandlerRegistry}.
 */
public interface HandlerResolver {

    <R, C extends Command<R>> CommandHandler<C, R> resolve(C command);

    /**
     * Resolves the handler for the given command and invokes it, returning the handler's result. Used by the
     * dispatch pipeline terminal link, which works with the erased (wildcard) command type.
     */
    Object handle(Command<?> command);
}
