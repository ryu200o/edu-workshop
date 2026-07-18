/**
 * Inbound query surface (read side). Holds query DTOs, response/projection DTOs (Java records
 * implementing {@code Query<R>}). The read entry point is the shared {@code QueryBus}
 * ({@code io.github.ryu200o.eduworkshop.shared.application.cqs.api}) — no longer declared per module (see ADR 0006).
 */
package io.github.ryu200o.eduworkshop.room.internal.application.port.in.query;
