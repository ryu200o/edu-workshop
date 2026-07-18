/**
 * Inbound command surface (write side). Holds command DTOs (Java records implementing
 * {@code Command<R>}). The write entry point is the shared {@code CommandBus}
 * ({@code io.github.ryu200o.eduworkshop.shared.kernel.bus}) — the only interface driving
 * adapters depend on; it is no longer declared per module (see ADR 0006).
 */
package io.github.ryu200o.eduworkshop.room.internal.application.port.in.command;
