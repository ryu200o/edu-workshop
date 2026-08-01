package io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.query;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;
import io.github.ryu200o.eduworkshop.shared.application.cqs.exception.MissingQueryHandlerException;

/**
 * {@link QueryHandlerResolver} backed by the lazy {@link QueryHandlerRegistry}. Missing handlers surface as
 * {@link MissingQueryHandlerException} at dispatch time.
 */
public final class RegistryQueryHandlerResolver implements QueryHandlerResolver {

    private final QueryHandlerRegistry registry;

    public RegistryQueryHandlerResolver(QueryHandlerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Object handle(Query<?> query) {
        return registry.handleQuery(query);
    }
}
