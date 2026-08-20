package io.github.ryu200o.eduworkshop.shared.application.cqs;

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
import io.github.ryu200o.eduworkshop.shared.application.cqs.exception.DuplicateCommandHandlerException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.exception.DuplicateQueryHandlerException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.exception.MissingCommandHandlerException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.exception.MissingQueryHandlerException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusDispatchTest {

    private record PingCommand(String name) implements Command {
    }

    private static final class PingHandler implements CommandHandler<PingCommand> {
        private final java.util.List<String> effects;

        PingHandler(java.util.List<String> effects) {
            this.effects = effects;
        }

        @Override
        public void handle(PingCommand command) {
            effects.add("pong:" + command.name());
        }
    }

    private record SizeQuery(String value) implements Query<Integer> {
    }

    private static final class SizeHandler implements QueryHandler<SizeQuery, Integer> {
        @Override
        public Integer handle(SizeQuery query) {
            return query.value().length();
        }
    }

    private record EchoQueryCommand(String value) implements Command {
    }

    private static final class EchoQueryCommandHandler implements CommandHandler<EchoQueryCommand> {
        private final QueryBus queryBus;
        private final java.util.List<String> effects;

        EchoQueryCommandHandler(QueryBus queryBus, java.util.List<String> effects) {
            this.queryBus = queryBus;
            this.effects = effects;
        }

        @Override
        public void handle(EchoQueryCommand command) {
            effects.add("echo:" + queryBus.execute(new SizeQuery(command.value())));
        }
    }

    @Configuration
    static class Cfg {
        @Bean
        PingHandler pingHandler() {
            return new PingHandler(effects());
        }

        @Bean
        java.util.List<String> effects() {
            return new java.util.ArrayList<>();
        }

        @Bean
        SizeHandler sizeHandler() {
            return new SizeHandler();
        }

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
        CommandDispatcher commandDispatcher(CommandHandlerResolver resolver, CommandPolicyResolver policyResolver,
                                            CommandPipeline defaultPipeline) {
            return new CommandDispatcher(resolver, policyResolver, defaultPipeline);
        }

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
    }

    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Cfg.class);

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void commandDispatch_resolvesHandlerAndAppliesSideEffect() {
        CommandDispatcher dispatcher = context.getBean(CommandDispatcher.class);
        dispatcher.dispatch(new PingCommand("room"));
        assertThat(context.getBean(java.util.List.class)).containsExactly("pong:room");
    }

    @Test
    void queryDispatch_resolvesHandlerAndReturnsResult() {
        QueryDispatcher dispatcher = context.getBean(QueryDispatcher.class);
        Object result = dispatcher.dispatch(new SizeQuery("abc"));
        assertThat(result).isEqualTo(3);
    }

    private static ObjectProvider<CommandHandler<?>> commandHandlerProvider(
            org.springframework.beans.factory.ListableBeanFactory factory) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        ObjectProvider<CommandHandler<?>> provider = (ObjectProvider) factory.getBeanProvider(CommandHandler.class);
        return provider;
    }

    private static ObjectProvider<QueryHandler<?, ?>> queryHandlerProvider(
            org.springframework.beans.factory.ListableBeanFactory factory) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        ObjectProvider<QueryHandler<?, ?>> provider = (ObjectProvider) factory.getBeanProvider(QueryHandler.class);
        return provider;
    }

    @Test
    void missingCommandHandler_failsFastWithDedicatedException() {
        org.springframework.beans.factory.support.StaticListableBeanFactory emptyFactory =
                new org.springframework.beans.factory.support.StaticListableBeanFactory();
        CommandHandlerRegistry registry = new CommandHandlerRegistry(commandHandlerProvider(emptyFactory));
        CommandHandlerResolver resolver = new RegistryCommandHandlerResolver(registry);
        CommandDispatcher dispatcher = new CommandDispatcher(resolver,
                new CompositeCommandPolicyResolver(List.of()), new CommandPipeline(List.of()));

        assertThatThrownBy(() -> dispatcher.dispatch(new PingCommand("x")))
                .isInstanceOf(MissingCommandHandlerException.class)
                .hasMessageContaining(PingCommand.class.getName());
    }

    @Test
    void missingQueryHandler_failsAtDispatchTime() {
        org.springframework.beans.factory.support.StaticListableBeanFactory emptyFactory =
                new org.springframework.beans.factory.support.StaticListableBeanFactory();
        QueryHandlerRegistry registry = new QueryHandlerRegistry(queryHandlerProvider(emptyFactory));
        QueryHandlerResolver resolver = new RegistryQueryHandlerResolver(registry);
        QueryDispatcher dispatcher = new QueryDispatcher(resolver);

        assertThatThrownBy(() -> dispatcher.dispatch(new SizeQuery("x")))
                .isInstanceOf(MissingQueryHandlerException.class)
                .hasMessageContaining(SizeQuery.class.getName());
    }

    @Test
    void duplicateCommandHandler_failsFastAtStartup() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TwoPingCfg.class);
        try {
            assertThatThrownBy(() -> new CommandHandlerRegistry(commandHandlerProvider(ctx)))
                    .isInstanceOf(DuplicateCommandHandlerException.class)
                    .hasMessageContaining(PingCommand.class.getName());
        } finally {
            ctx.close();
        }
    }

    @Test
    void duplicateQueryHandler_failsAtDispatchTime() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TwoSizeCfg.class);
        try {
            QueryHandlerRegistry registry = new QueryHandlerRegistry(queryHandlerProvider(ctx));
            QueryHandlerResolver resolver = new RegistryQueryHandlerResolver(registry);
            QueryDispatcher dispatcher = new QueryDispatcher(resolver);

            assertThatThrownBy(() -> dispatcher.dispatch(new SizeQuery("x")))
                    .isInstanceOf(DuplicateQueryHandlerException.class)
                    .hasMessageContaining(SizeQuery.class.getName());
        } finally {
            ctx.close();
        }
    }

    @Test
    void commandHandlerDependingOnQueryBus_startsAcyclicallyAndDispatches() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(CycleFreeCfg.class);
        try {
            CommandBus commandBus = ctx.getBean(CommandBus.class);
            commandBus.execute(new EchoQueryCommand("room"));
            assertThat(ctx.getBean("effects", java.util.List.class)).containsExactly("echo:4");
        } finally {
            ctx.close();
        }
    }

    @Configuration
    static class TwoPingCfg {
        @Bean
        PingHandler a() {
            return new PingHandler(new java.util.ArrayList<>());
        }

        @Bean
        PingHandler b() {
            return new PingHandler(new java.util.ArrayList<>());
        }
    }

    @Configuration
    static class TwoSizeCfg {
        @Bean
        SizeHandler a() {
            return new SizeHandler();
        }

        @Bean
        SizeHandler b() {
            return new SizeHandler();
        }
    }

    /**
     * Mirrors the real post-publish scenario that previously forced a startup cycle: a CommandHandler
     * depends on the shared {@link QueryBus}. Because the query registry is resolved lazily at runtime
     * (never eagerly scanned while the command registry is being built), the context must start without
     * {@code @Lazy} anywhere.
     */
    @Configuration
    static class CycleFreeCfg {
        @Bean
        SizeHandler sizeHandler() {
            return new SizeHandler();
        }

        @Bean
        EchoQueryCommandHandler echoQueryCommandHandler(QueryBus queryBus, java.util.List<String> effects) {
            return new EchoQueryCommandHandler(queryBus, effects);
        }

        @Bean
        java.util.List<String> effects() {
            return new java.util.ArrayList<>();
        }

        @Bean
        CommandHandlerRegistry commandHandlerRegistry(ObjectProvider<CommandHandler<?>> commandHandlers) {
            return new CommandHandlerRegistry(commandHandlers);
        }

        @Bean
        CommandHandlerResolver commandHandlerResolver(CommandHandlerRegistry registry) {
            return new RegistryCommandHandlerResolver(registry);
        }

        @Bean
        QueryHandlerRegistry queryHandlerRegistry(ObjectProvider<QueryHandler<?, ?>> queryHandlers) {
            return new QueryHandlerRegistry(queryHandlers);
        }

        @Bean
        QueryHandlerResolver queryHandlerResolver(QueryHandlerRegistry registry) {
            return new RegistryQueryHandlerResolver(registry);
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
        CommandDispatcher commandDispatcher(CommandHandlerResolver resolver, CommandPolicyResolver policyResolver,
                                            CommandPipeline defaultPipeline) {
            return new CommandDispatcher(resolver, policyResolver, defaultPipeline);
        }

        @Bean
        QueryDispatcher queryDispatcher(QueryHandlerResolver resolver) {
            return new QueryDispatcher(resolver);
        }

        @Bean
        CommandBus commandBus(CommandDispatcher dispatcher) {
            return new CommandBus() {
                @Override
                public void execute(Command command) {
                    dispatcher.dispatch(command);
                }
            };
        }

        @Bean
        QueryBus queryBus(QueryDispatcher dispatcher) {
            return new QueryBus() {
                @Override
                @SuppressWarnings("unchecked")
                public <R, Q extends Query<R>> R execute(Q query) {
                    return (R) dispatcher.dispatch(query);
                }
            };
        }
    }
}
