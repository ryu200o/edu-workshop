/**
 * Inbound command surface (write side). Holds command DTOs (Java records implementing
 * {@code Command<R>}) and the {@code CommandBus} interface — the only write entry point other
 * layers (e.g. driving adapters) may depend on.
 */
package io.github.ryu200o.eduworkshop.room.internal.application.port.in.command;
