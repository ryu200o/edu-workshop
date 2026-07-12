package io.github.ryu200o.eduworkshop.shared.cqs;

/**
 * Handles a specific {@link Command} type and produces its result.
 *
 * @param <C> the command type handled
 * @param <R> the result type produced
 */
public interface CommandHandler<C extends Command<R>, R> {

    R handle(C command);
}
