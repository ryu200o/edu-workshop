package io.github.ryu200o.eduworkshop.shared.application.cqs.api;

/**
 * Handles a specific {@link Query} type and produces its result.
 *
 * @param <Q> the query type handled
 * @param <R> the result type produced
 */
public interface QueryHandler<Q extends Query<R>, R> {

    R handle(Q query);
}
