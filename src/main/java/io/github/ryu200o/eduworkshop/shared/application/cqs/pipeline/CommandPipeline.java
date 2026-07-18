package io.github.ryu200o.eduworkshop.shared.application.cqs.pipeline;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.List;

/**
 * A Chain of Responsibility of {@link CommandBehavior} units terminating in the resolved
 * {@link io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler}. Knows the ordered behavior chain, not
 * Spring or the dispatcher internals.
 */
public class CommandPipeline {

    private final List<CommandBehavior> behaviors;

    public CommandPipeline(List<CommandBehavior> behaviors) {
        this.behaviors = List.copyOf(behaviors);
    }

    public Object run(Command<?> command, CommandHandlerInvoker terminal) {
        return new Chain(0, terminal).next(command);
    }

    @FunctionalInterface
    public interface CommandHandlerInvoker {
        Object invoke(Command<?> command);
    }

    private final class Chain implements BehaviorChain {
        private final int index;
        private final CommandHandlerInvoker terminal;

        private Chain(int index, CommandHandlerInvoker terminal) {
            this.index = index;
            this.terminal = terminal;
        }

        @Override
        public Object next(Command<?> command) {
            if (index < behaviors.size()) {
                return behaviors.get(index).handle(command, new Chain(index + 1, terminal));
            }
            return terminal.invoke(command);
        }
    }
}
