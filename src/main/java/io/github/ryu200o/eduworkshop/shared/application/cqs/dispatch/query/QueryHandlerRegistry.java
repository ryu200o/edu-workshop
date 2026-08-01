package io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.query;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler;
import io.github.ryu200o.eduworkshop.shared.application.cqs.exception.DuplicateQueryHandlerException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.exception.MissingQueryHandlerException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ResolvableType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of {@link QueryHandler} beans, resolved lazily from an {@link ObjectProvider} on first use
 * instead of being scanned eagerly at startup. This is deliberate: query handlers may themselves need
 * shared-bus beans ({@code QueryBus} / {@code CommandBus}), so an eager startup scan would create a
 * circular bean-creation dependency with the dispatch infrastructure. Deferring the scan to runtime —
 * on the first dispatched query — keeps the startup graph acyclic without {@code @Lazy}.
 */
public class QueryHandlerRegistry {

    private final ObjectProvider<QueryHandler<?, ?>> queryHandlerProvider;

    private volatile Map<Class<?>, QueryHandler<?, ?>> queryHandlers;

    public QueryHandlerRegistry(ObjectProvider<QueryHandler<?, ?>> queryHandlerProvider) {
        this.queryHandlerProvider = queryHandlerProvider;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Object handleQuery(Query<?> query) {
        QueryHandler handler = handlers().get(query.getClass());
        if (handler == null) {
            throw new MissingQueryHandlerException(query.getClass());
        }
        return handler.handle(query);
    }

    private Map<Class<?>, QueryHandler<?, ?>> handlers() {
        Map<Class<?>, QueryHandler<?, ?>> current = this.queryHandlers;
        if (current == null) {
            synchronized (this) {
                current = this.queryHandlers;
                if (current == null) {
                    current = scan();
                    this.queryHandlers = current;
                }
            }
        }
        return current;
    }

    private Map<Class<?>, QueryHandler<?, ?>> scan() {
        Map<Class<?>, QueryHandler<?, ?>> scanned = new LinkedHashMap<>();
        for (QueryHandler<?, ?> handler : queryHandlerProvider.orderedStream().toList()) {            Class<?> queryType = queryTypeOf(handler);
            if (queryType == null) {
                continue;
            }
            if (scanned.putIfAbsent(queryType, handler) != null) {
                throw new DuplicateQueryHandlerException(queryType);
            }
        }
        return Map.copyOf(scanned);
    }

    private static Class<?> queryTypeOf(Object handler) {
        ResolvableType type = ResolvableType.forClass(handler.getClass()).as(QueryHandler.class);
        return type.getGeneric(0).resolve();
    }
}
