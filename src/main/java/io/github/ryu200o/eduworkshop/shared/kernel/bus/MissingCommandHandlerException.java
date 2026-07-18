package io.github.ryu200o.eduworkshop.shared.kernel.bus;

/**
 * Thrown when a command is dispatched but no {@code CommandHandler} bean is registered for its type.
 */
public class MissingCommandHandlerException extends IllegalStateException {

    public MissingCommandHandlerException(Class<?> commandType) {
        super("No command handler found for command type: " + commandType.getName()
                + ". Register a CommandHandler<C, R> bean for this command.");
    }
}
