package io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;

/**
 * Resolves a {@link CommandHandler} for a concrete {@link Command} type. Pure lookup: it knows nothing
 * about Spring, the dispatch pipeline, or policy. The mapping is sourced from the immutable
 * {@link CommandHandlerRegistry}.
 */
public interface CommandHandlerResolver {

    <C extends Command> CommandHandler<C> resolve(C command);

    /**
     * Resolves the handler for the given command and invokes it as a side-effect. Used by the
     * dispatch pipeline terminal link, which works with the erased (wildcard) command type.
     */
    void handle(Command command);
}
