package io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;
import io.github.ryu200o.eduworkshop.shared.application.cqs.pipeline.CommandPipeline;
import io.github.ryu200o.eduworkshop.shared.application.cqs.pipeline.CommandPolicyResolver;

/**
 * Command dispatch coordinator. Orchestrates resolution and execution but owns no business logic and knows
 * nothing about Spring or pipeline internals: it asks the {@link HandlerResolver} for the handler and the
 * {@link CommandPolicyResolver} for the matching {@link CommandPipeline}, then runs the chain that terminates
 * in the handler.
 */
public class CommandDispatcher {

    private final HandlerResolver resolver;
    private final CommandPolicyResolver policyResolver;
    private final CommandPipeline defaultPipeline;

    public CommandDispatcher(HandlerResolver resolver, CommandPolicyResolver policyResolver,
                             CommandPipeline defaultPipeline) {
        this.resolver = resolver;
        this.policyResolver = policyResolver;
        this.defaultPipeline = defaultPipeline;
    }

    /**
     * Runs the command through its resolved pipeline chain. The parameter is declared as {@link Object} rather
     * than {@link Command} on purpose: the Modulith observability interceptor formats intercepted method
     * signatures, and a generic {@code Command<R>} argument normalizes to {@code Command<?>} whose unbounded
     * wildcard resolves to {@code null}, crashing the formatter.
     */
    @SuppressWarnings("rawtypes")
    public Object dispatch(Object command) {
        Command c = (Command) command;
        CommandPipeline pipeline = policyResolver.resolve(c).orElse(defaultPipeline);
        return pipeline.run(c, resolver::handle);
    }
}
