package io.github.ryu200o.eduworkshop.shared.kernel.bus;

import io.github.ryu200o.eduworkshop.shared.cqs.Command;

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

    public Object dispatch(Command<?> command) {
        CommandPipeline pipeline = policyResolver.resolve(command).orElse(defaultPipeline);
        return pipeline.run(command, resolver::handle);
    }
}
