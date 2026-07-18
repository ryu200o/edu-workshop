package io.github.ryu200o.eduworkshop.shared.kernel.bus;

import io.github.ryu200o.eduworkshop.shared.cqs.Command;

/**
 * Shared command bus — the public write entry point for driving adapters (controllers, module_api).
 * Modules depend only on this interface; the dispatch implementation lives in the shared kernel.
 *
 * @see CommandDispatcher
 */
public interface CommandBus {

    <R, C extends Command<R>> R execute(C command);
}
