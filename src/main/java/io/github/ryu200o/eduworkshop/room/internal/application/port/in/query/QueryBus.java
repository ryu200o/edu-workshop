package io.github.ryu200o.eduworkshop.room.internal.application.port.in.query;

import io.github.ryu200o.eduworkshop.shared.cqs.Query;

/**
 * Room module's own query bus — the public read entry point for driving adapters. Each module owns
 * its bus (intentional duplication) to protect Spring Modulith boundaries; handlers are resolved only
 * within this module's context.
 */
public interface QueryBus {

    <R, Q extends Query<R>> R execute(Q query);
}
