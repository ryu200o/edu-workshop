package io.github.ryu200o.eduworkshop.shared.cqs;

/**
 * Marker for a read-side query that, when handled, yields a result of type {@code R}.
 *
 * <p>Part of the global CQS shared kernel. Queries are plain DTOs (prefer Java records) carrying only
 * the lookup criteria and no behavior. Handling a query must be side-effect free.</p>
 *
 * @param <R> the type produced by handling this query
 */
public interface Query<R> {
}
