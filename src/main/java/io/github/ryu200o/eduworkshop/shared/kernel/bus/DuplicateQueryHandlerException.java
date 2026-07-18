package io.github.ryu200o.eduworkshop.shared.kernel.bus;

/**
 * Thrown at startup when two or more {@code QueryHandler} beans claim the same query type, so the
 * dispatcher cannot pick a single handler unambiguously.
 */
public class DuplicateQueryHandlerException extends IllegalStateException {

    public DuplicateQueryHandlerException(Class<?> queryType) {
        super("Multiple query handlers registered for query type: " + queryType.getName()
                + ". Each query type must have exactly one handler.");
    }
}
