package io.github.ryu200o.eduworkshop.shared.application.cqs.api;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

/**
 * Shared command bus — the public write entry point for inbound adapters (controllers, module_api).
 * Modules depend only on this interface; the dispatch implementation lives in the shared kernel.
 *
 * @see io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.command.CommandDispatcher
 */
public interface CommandBus {

    void execute(Command command);
}
