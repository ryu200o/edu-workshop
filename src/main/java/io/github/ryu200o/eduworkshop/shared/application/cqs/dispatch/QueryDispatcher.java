package io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;

/**
 * Query dispatch coordinator. Resolves the {@link io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler} for
 * a query type via the {@link HandlerRegistry} and invokes it. Queries are side-effect free, so no behavior
 * chain is applied (read-only projection lookup).
 */
public class QueryDispatcher {

    private final HandlerRegistry registry;

    public QueryDispatcher(HandlerRegistry registry) {
        this.registry = registry;
    }

    public Object dispatch(Query<?> query) {
        return registry.handleQuery(query);
    }
}
