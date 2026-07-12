package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.QueryBus;
import io.github.ryu200o.eduworkshop.shared.cqs.Query;
import io.github.ryu200o.eduworkshop.shared.cqs.QueryHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

/**
 * Spring-context-backed query bus. Resolves the single {@link QueryHandler} bean whose generic query
 * type matches the incoming query, via {@link ResolvableType}. Package-private: only the
 * {@link QueryBus} interface is exposed outside this package.
 */
@Component
class SimpleQueryBus implements QueryBus {

    private final ApplicationContext context;

    SimpleQueryBus(ApplicationContext context) {
        this.context = context;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R, Q extends Query<R>> R execute(Q query) {
        ResolvableType type = ResolvableType.forClassWithGenerics(
                QueryHandler.class, query.getClass(), Object.class);
        String[] beanNames = context.getBeanNamesForType(type);

        if (beanNames.length == 0) {
            throw new IllegalStateException(
                    "No handler found for query: " + query.getClass().getSimpleName());
        }

        QueryHandler<Q, R> handler = (QueryHandler<Q, R>) context.getBean(beanNames[0]);
        return handler.handle(query);
    }
}
