package io.github.ryu200o.eduworkshop.shared.application.cqs.api;

/**
 * Marker for a write-side command. Handling a command is a pure side-effect (void): it mutates
 * state and never returns a result.
 *
 * <p>Part of the global CQS shared kernel. Commands are plain DTOs (prefer Java records) and carry
 * no behavior.</p>
 */
public interface Command {
}
