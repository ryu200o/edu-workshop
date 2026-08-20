package io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.shared.application.cqs.exception.MissingCommandHandlerException;

/**
 * {@link CommandHandlerResolver} backed by the immutable {@link CommandHandlerRegistry}. Looks up the handler
 * for a command type; missing handlers surface as {@link MissingCommandHandlerException}.
 */
public final class RegistryCommandHandlerResolver implements CommandHandlerResolver {

    private final CommandHandlerRegistry registry;

    public RegistryCommandHandlerResolver(CommandHandlerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public <C extends Command> CommandHandler<C> resolve(C command) {
        return registry.commandHandler(command.getClass());
    }

    @Override
    public void handle(Command command) {
        registry.handleCommand(command);
    }
}
