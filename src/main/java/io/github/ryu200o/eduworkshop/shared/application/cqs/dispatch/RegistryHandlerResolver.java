package io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;

/**
 * {@link HandlerResolver} backed by the immutable {@link HandlerRegistry}. Looks up the handler for a command
 * type; missing handlers surface as {@link MissingCommandHandlerException}.
 */
public final class RegistryHandlerResolver implements HandlerResolver {

    private final HandlerRegistry registry;

    public RegistryHandlerResolver(HandlerRegistry registry) {
        this.registry = registry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R, C extends Command<R>> CommandHandler<C, R> resolve(C command) {
        return registry.<R, C>commandHandler(command.getClass());
    }

    @Override
    public Object handle(Command<?> command) {
        return registry.handleCommand(command);
    }
}
