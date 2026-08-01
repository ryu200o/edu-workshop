package io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.query;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler;

/**
 * Resolves a {@link QueryHandler} for a concrete {@link Query} type. Pure lookup: it knows nothing
 * about Spring or the dispatch coordinator. The mapping is sourced from the lazy {@link QueryHandlerRegistry}.
 */
public interface QueryHandlerResolver {

    /**
     * Resolves the handler for the given query and invokes it, returning the handler's result. Queries are
     * side-effect free, so no behavior chain is applied.
     */
    Object handle(Query<?> query);
}
