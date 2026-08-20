package io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.shared.application.cqs.exception.DuplicateCommandHandlerException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.exception.MissingCommandHandlerException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ResolvableType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable registry of command handlers, built once at startup and then frozen. It discovers all
 * {@link CommandHandler} beans in the application context through an {@link ObjectProvider}, validates
 * that each command type has exactly one handler (failing fast with a dedicated exception), and becomes
 * read-only at runtime — no re-scan, no re-compute, no synchronization.
 *
 * <p>Query handlers are intentionally <em>not</em> part of this registry: they are resolved lazily at runtime
 * through the separate query subsystem, so that query handlers may themselves depend on the shared buses
 * without creating a circular bean-creation dependency at startup.</p>
 */
public class CommandHandlerRegistry {

    private final Map<Class<?>, CommandHandler<?>> commandHandlers;

    public CommandHandlerRegistry(ObjectProvider<CommandHandler<?>> commandHandlerProvider) {
        this.commandHandlers = Map.copyOf(scan(commandHandlerProvider));
    }

    @SuppressWarnings("unchecked")
    <C extends Command> CommandHandler<C> commandHandler(Class<?> commandType) {
        CommandHandler<?> handler = commandHandlers.get(commandType);
        if (handler == null) {
            throw new MissingCommandHandlerException(commandType);
        }
        return (CommandHandler<C>) handler;
    }

    void handleCommand(Command command) {
        commandHandler(command.getClass()).handle(command);
    }

    private static Map<Class<?>, CommandHandler<?>> scan(ObjectProvider<CommandHandler<?>> provider) {
        Map<Class<?>, CommandHandler<?>> commandHandlers = new LinkedHashMap<>();
        for (CommandHandler<?> handler : provider.orderedStream().toList()) {
            Class<?> commandType = commandTypeOf(handler);
            if (commandType == null) {
                continue;
            }
            if (commandHandlers.putIfAbsent(commandType, handler) != null) {
                throw new DuplicateCommandHandlerException(commandType);
            }
        }
        return commandHandlers;
    }

    private static Class<?> commandTypeOf(Object handler) {
        ResolvableType type = ResolvableType.forClass(handler.getClass()).as(CommandHandler.class);
        return type.getGeneric(0).resolve();
    }
}
