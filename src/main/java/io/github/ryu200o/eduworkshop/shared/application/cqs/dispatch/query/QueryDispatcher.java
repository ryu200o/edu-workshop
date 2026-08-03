package io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.query;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;

/**
 * Query dispatch coordinator. Resolves the {@link io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler} for
 * a query type via the {@link QueryHandlerResolver} and invokes it. Queries are side-effect free, so no behavior
 * chain is applied (read-only projection lookup).
 */
public class QueryDispatcher {

    private final QueryHandlerResolver resolver;

    public QueryDispatcher(QueryHandlerResolver resolver) {
        this.resolver = resolver;
    }

    public Object dispatch(Object query) {
        return resolver.handle((Query<?>) query);
    }
}
