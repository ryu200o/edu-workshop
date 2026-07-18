package io.github.ryu200o.eduworkshop.shared.kernel.bus;

/**
 * Thrown at startup when two or more {@code CommandHandler} beans claim the same command type, so the
 * dispatcher cannot pick a single handler unambiguously.
 */
public class DuplicateCommandHandlerException extends IllegalStateException {

    public DuplicateCommandHandlerException(Class<?> commandType) {
        super("Multiple command handlers registered for command type: " + commandType.getName()
                + ". Each command type must have exactly one handler.");
    }
}
