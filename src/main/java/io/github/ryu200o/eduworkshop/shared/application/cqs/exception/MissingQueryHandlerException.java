package io.github.ryu200o.eduworkshop.shared.application.cqs.exception;

/**
 * Thrown when a query is dispatched but no {@code QueryHandler} bean is registered for its type.
 */
public class MissingQueryHandlerException extends IllegalStateException {

    public MissingQueryHandlerException(Class<?> queryType) {
        super("No query handler found for query type: " + queryType.getName()
                + ". Register a QueryHandler<Q, R> bean for this query.");
    }
}
