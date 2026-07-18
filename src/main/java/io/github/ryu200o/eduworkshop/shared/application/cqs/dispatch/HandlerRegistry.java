package io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler;
import io.github.ryu200o.eduworkshop.shared.application.cqs.exception.DuplicateCommandHandlerException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.exception.DuplicateQueryHandlerException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.exception.MissingCommandHandlerException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.exception.MissingQueryHandlerException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.ResolvableType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable registry of command/query handlers, built once at startup and then frozen. It discovers all
 * {@link CommandHandler} and {@link QueryHandler} beans in the application context via {@link ResolvableType},
 * validates that each command/query type has exactly one handler (failing fast with a dedicated exception),
 * and becomes read-only at runtime — no re-scan, no re-compute, no synchronization.
 */
public class HandlerRegistry {

    private final Map<Class<?>, CommandHandler<?, ?>> commandHandlers;
    private final Map<Class<?>, QueryHandler<?, ?>> queryHandlers;

    HandlerRegistry(Map<Class<?>, CommandHandler<?, ?>> commandHandlers,
                            Map<Class<?>, QueryHandler<?, ?>> queryHandlers) {
        this.commandHandlers = Map.copyOf(commandHandlers);
        this.queryHandlers = Map.copyOf(queryHandlers);
    }

    @SuppressWarnings("unchecked")
    <R, C extends Command<R>> CommandHandler<C, R> commandHandler(Class<?> commandType) {
        CommandHandler<?, ?> handler = commandHandlers.get(commandType);
        if (handler == null) {
            throw new MissingCommandHandlerException(commandType);
        }
        return (CommandHandler<C, R>) handler;
    }

    @SuppressWarnings("unchecked")
    <R, Q extends Query<R>> QueryHandler<Q, R> queryHandler(Class<?> queryType) {
        QueryHandler<?, ?> handler = queryHandlers.get(queryType);
        if (handler == null) {
            throw new MissingQueryHandlerException(queryType);
        }
        return (QueryHandler<Q, R>) handler;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Object handleCommand(Command<?> command) {
        CommandHandler handler = commandHandlers.get(command.getClass());
        if (handler == null) {
            throw new MissingCommandHandlerException(command.getClass());
        }
        return handler.handle(command);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Object handleQuery(Query<?> query) {
        QueryHandler handler = queryHandlers.get(query.getClass());
        if (handler == null) {
            throw new MissingQueryHandlerException(query.getClass());
        }
        return handler.handle(query);
    }

    public static HandlerRegistry from(ListableBeanFactory beanFactory) {
        Map<Class<?>, CommandHandler<?, ?>> commandHandlers = new LinkedHashMap<>();
        Map<Class<?>, QueryHandler<?, ?>> queryHandlers = new LinkedHashMap<>();

        for (CommandHandler<?, ?> handler : beanFactory.getBeansOfType(CommandHandler.class).values()) {
            Class<?> commandType = commandTypeOf(handler, CommandHandler.class, 0);
            if (commandType == null) {
                continue;
            }
            if (commandHandlers.putIfAbsent(commandType, handler) != null) {
                throw new DuplicateCommandHandlerException(commandType);
            }
        }
        for (QueryHandler<?, ?> handler : beanFactory.getBeansOfType(QueryHandler.class).values()) {
            Class<?> queryType = commandTypeOf(handler, QueryHandler.class, 0);
            if (queryType == null) {
                continue;
            }
            if (queryHandlers.putIfAbsent(queryType, handler) != null) {
                throw new DuplicateQueryHandlerException(queryType);
            }
        }

        return new HandlerRegistry(commandHandlers, queryHandlers);
    }

    private static Class<?> commandTypeOf(Object handler, Class<?> handlerInterface, int index) {
        ResolvableType type = ResolvableType.forClass(handler.getClass()).as(handlerInterface);
        return type.getGeneric(index).resolve();
    }
}
