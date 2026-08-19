package io.github.ryu200o.eduworkshop.shared.application.cqs.api;

/**
 * Handles a specific {@link Command} type as a pure side-effect.
 *
 * <p>Handlers never return a value (Strict CQS / ADR 0021): any result a client needs is fetched
 * through the query side afterwards.</p>
 *
 * @param <C> the command type handled
 */
public interface CommandHandler<C extends Command> {

    void handle(C command);
}
