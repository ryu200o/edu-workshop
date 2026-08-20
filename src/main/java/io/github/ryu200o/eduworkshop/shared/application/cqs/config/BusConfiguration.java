package io.github.ryu200o.eduworkshop.shared.application.cqs.config;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryBus;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler;
import io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.command.CommandDispatcher;
import io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.command.CommandHandlerRegistry;
import io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.command.CommandHandlerResolver;
import io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.command.RegistryCommandHandlerResolver;
import io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.command.pipeline.CommandPipeline;
import io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.command.pipeline.CommandPolicyResolver;
import io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.command.pipeline.CompositeCommandPolicyResolver;
import io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.query.QueryDispatcher;
import io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.query.QueryHandlerRegistry;
import io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.query.QueryHandlerResolver;
import io.github.ryu200o.eduworkshop.shared.application.cqs.dispatch.query.RegistryQueryHandlerResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Declares the shared bus capability as Spring beans, split into two fully symmetric subsystems.
 *
 * <p>1. Command subsystem (write / mutate): eager {@link CommandHandlerRegistry} (fails fast on
 * duplicate/missing handlers at startup) feeding {@link CommandHandlerResolver}, the optional
 * module-contributed {@link CommandPipeline}s via {@link CommandPolicyResolver}, and the
 * {@link CommandDispatcher} coordinator that runs commands through their pipeline chain.</p>
 *
 * <p>2. Query subsystem (read / projection): lazy {@link QueryHandlerRegistry} (resolved at runtime on first
 * dispatch, keeping startup acyclic when query handlers depend on the buses) feeding {@link QueryHandlerResolver}
 * and the zero-pipeline {@link QueryDispatcher}.</p>
 *
 * <p>Both registries gather their handler beans via {@link ObjectProvider} — no {@code @Lazy}, no proxy,
 * no startup bean cycle. Modules contribute optional {@link CommandPolicyResolver.ModuleRegistration} beans
 * to customize their command pipelines; absent those, a default pass-through pipeline is used.</p>
 */
@Configuration
public class BusConfiguration {

    // =========================================================================
    // 1. COMMAND SUBSYSTEM (Ghi / Mutate State)
    // =========================================================================

    @Bean
    CommandHandlerRegistry commandHandlerRegistry(ObjectProvider<CommandHandler<?>> commandHandlers) {
        return new CommandHandlerRegistry(commandHandlers);
    }

    @Bean
    CommandHandlerResolver commandHandlerResolver(CommandHandlerRegistry registry) {
        return new RegistryCommandHandlerResolver(registry);
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
    CommandDispatcher commandDispatcher(CommandHandlerResolver resolver,
                                        CommandPolicyResolver policyResolver,
                                        CommandPipeline defaultPipeline) {
        return new CommandDispatcher(resolver, policyResolver, defaultPipeline);
    }

    // =========================================================================
    // 2. QUERY SUBSYSTEM (Đọc / Pure Projections)
    // =========================================================================

    @Bean
    QueryHandlerRegistry queryHandlerRegistry(ObjectProvider<QueryHandler<?, ?>> queryHandlers) {
        return new QueryHandlerRegistry(queryHandlers);
    }

    @Bean
    QueryHandlerResolver queryHandlerResolver(QueryHandlerRegistry registry) {
        return new RegistryQueryHandlerResolver(registry);
    }

    @Bean
    QueryDispatcher queryDispatcher(QueryHandlerResolver resolver) {
        return new QueryDispatcher(resolver);
    }

    // =========================================================================
    // 3. DELEGATING BUSES
    // =========================================================================

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
        public void execute(Command command) {
            dispatcher.dispatch(command);
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
