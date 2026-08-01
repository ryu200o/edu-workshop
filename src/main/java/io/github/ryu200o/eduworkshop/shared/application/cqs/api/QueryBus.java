package io.github.ryu200o.eduworkshop.shared.application.cqs.api;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;

/**
 * Shared query bus — the public read entry point for inbound adapters. Modules depend only on this
 * interface; the dispatch implementation lives in the shared kernel.
 *
 * @see io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.query.QueryDispatcher
 */
public interface QueryBus {

    <R, Q extends Query<R>> R execute(Q query);
}
