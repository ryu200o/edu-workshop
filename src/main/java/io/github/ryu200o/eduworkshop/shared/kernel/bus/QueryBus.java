package io.github.ryu200o.eduworkshop.shared.kernel.bus;

import io.github.ryu200o.eduworkshop.shared.cqs.Query;

/**
 * Shared query bus — the public read entry point for driving adapters. Modules depend only on this
 * interface; the dispatch implementation lives in the shared kernel.
 *
 * @see QueryDispatcher
 */
public interface QueryBus {

    <R, Q extends Query<R>> R execute(Q query);
}
