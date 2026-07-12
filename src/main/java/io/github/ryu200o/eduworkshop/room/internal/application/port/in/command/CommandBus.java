package io.github.ryu200o.eduworkshop.room.internal.application.port.in.command;

import io.github.ryu200o.eduworkshop.shared.cqs.Command;

/**
 * Room module's own command bus — the public write entry point for driving adapters (controllers,
 * module_api). Each module owns its bus (intentional duplication) to protect Spring Modulith
 * boundaries; handlers are resolved only within this module's context.
 */
public interface CommandBus {

    <R, C extends Command<R>> R execute(C command);
}
