package io.github.ryu200o.eduworkshop.shared.kernel.bus;

import io.github.ryu200o.eduworkshop.shared.cqs.Command;
import io.github.ryu200o.eduworkshop.shared.cqs.Query;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Declares the shared bus capability as Spring beans. Builds the immutable {@link HandlerRegistry} once at
 * startup (failing fast on duplicate/missing handlers), wires the {@link CommandDispatcher} /
 * {@link QueryDispatcher} coordinators, and exposes the shared {@link CommandBus} / {@link QueryBus}
 * interfaces. Modules contribute optional {@link CommandPolicyResolver.ModuleRegistration} beans to customize
 * their command pipelines; absent those, a default pass-through pipeline is used.
 */
@Configuration
public class BusConfiguration {

    @Bean
    HandlerRegistry handlerRegistry(ListableBeanFactory beanFactory) {
        return HandlerRegistry.from(beanFactory);
    }

    @Bean
    HandlerResolver handlerResolver(HandlerRegistry registry) {
        return new RegistryHandlerResolver(registry);
    }

    @Bean
    CommandPipeline defaultCommandPipeline() {
        return new CommandPipeline(List.of());
    }

    @Bean
    CommandPolicyResolver commandPolicyResolver(List<CommandPolicyResolver.ModuleRegistration> registrations) {
        return new CompositeCommandPolicyResolver(registrations);
    }

    @Bean
    CommandDispatcher commandDispatcher(HandlerResolver resolver,
                                        CommandPolicyResolver policyResolver,
                                        CommandPipeline defaultPipeline) {
        return new CommandDispatcher(resolver, policyResolver, defaultPipeline);
    }

    @Bean
    QueryDispatcher queryDispatcher(HandlerRegistry registry) {
        return new QueryDispatcher(registry);
    }

    @Bean
    CommandBus commandBus(CommandDispatcher dispatcher) {
        return new DelegatingCommandBus(dispatcher);
    }

    @Bean
    QueryBus queryBus(QueryDispatcher dispatcher) {
        return new DelegatingQueryBus(dispatcher);
    }

    private static final class DelegatingCommandBus implements CommandBus {
        private final CommandDispatcher dispatcher;

        private DelegatingCommandBus(CommandDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R, C extends Command<R>> R execute(C command) {
            return (R) dispatcher.dispatch(command);
        }
    }

    private static final class DelegatingQueryBus implements QueryBus {
        private final QueryDispatcher dispatcher;

        private DelegatingQueryBus(QueryDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R, Q extends Query<R>> R execute(Q query) {
            return (R) dispatcher.dispatch(query);
        }
    }
}
