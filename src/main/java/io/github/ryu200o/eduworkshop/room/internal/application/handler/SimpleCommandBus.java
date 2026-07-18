package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.CommandBus;
import io.github.ryu200o.eduworkshop.shared.cqs.Command;
import io.github.ryu200o.eduworkshop.shared.cqs.CommandHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

/**
 * Spring-context-backed command bus. Resolves the single {@link CommandHandler} bean whose generic
 * command type matches the incoming command, via {@link ResolvableType}. Package-private: only the
 * {@link CommandBus} interface is exposed outside this package.
 */
@Component
class SimpleCommandBus implements CommandBus {

    private final ApplicationContext context;

    SimpleCommandBus(ApplicationContext context) {
        this.context = context;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R, C extends Command<R>> R execute(C command) {
        ResolvableType type = ResolvableType.forClassWithGenerics(
                CommandHandler.class, command.getClass(), Object.class);
        String[] beanNames = context.getBeanNamesForType(type);

        if (beanNames.length == 0) {
            throw new IllegalStateException(
                    "No handler found for command: " + command.getClass().getSimpleName());
        }

        CommandHandler<C, R> handler = (CommandHandler<C, R>) context.getBean(beanNames[0]);
        return handler.handle(command);
    }
}
