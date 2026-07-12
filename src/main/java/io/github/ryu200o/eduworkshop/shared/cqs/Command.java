package io.github.ryu200o.eduworkshop.shared.cqs;

/**
 * Marker for a write-side command that, when handled, yields a result of type {@code R}.
 *
 * <p>Part of the global CQS shared kernel. Commands are plain DTOs (prefer Java records) and carry
 * no behavior.</p>
 *
 * @param <R> the type produced by handling this command
 */
public interface Command<R> {
}
