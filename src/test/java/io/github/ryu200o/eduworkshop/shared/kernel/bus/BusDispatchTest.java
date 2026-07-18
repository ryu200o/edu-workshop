package io.github.ryu200o.eduworkshop.shared.kernel.bus;

import io.github.ryu200o.eduworkshop.shared.cqs.Command;
import io.github.ryu200o.eduworkshop.shared.cqs.CommandHandler;
import io.github.ryu200o.eduworkshop.shared.cqs.Query;
import io.github.ryu200o.eduworkshop.shared.cqs.QueryHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusDispatchTest {

    private record PingCommand(String name) implements Command<String> {
    }

    private static final class PingHandler implements CommandHandler<PingCommand, String> {
        @Override
        public String handle(PingCommand command) {
            return "pong:" + command.name();
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

    @Configuration
    static class Cfg {
        @Bean
        PingHandler pingHandler() {
            return new PingHandler();
        }

        @Bean
        SizeHandler sizeHandler() {
            return new SizeHandler();
        }

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
        CommandDispatcher commandDispatcher(HandlerResolver resolver, CommandPolicyResolver policyResolver,
                                            CommandPipeline defaultPipeline) {
            return new CommandDispatcher(resolver, policyResolver, defaultPipeline);
        }

        @Bean
        QueryDispatcher queryDispatcher(HandlerRegistry registry) {
            return new QueryDispatcher(registry);
        }
    }

    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Cfg.class);

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void commandDispatch_resolvesHandlerAndReturnsResult() {
        CommandDispatcher dispatcher = context.getBean(CommandDispatcher.class);
        Object result = dispatcher.dispatch(new PingCommand("room"));
        assertThat(result).isEqualTo("pong:room");
    }

    @Test
    void queryDispatch_resolvesHandlerAndReturnsResult() {
        QueryDispatcher dispatcher = context.getBean(QueryDispatcher.class);
        Object result = dispatcher.dispatch(new SizeQuery("abc"));
        assertThat(result).isEqualTo(3);
    }

    @Test
    void missingCommandHandler_failsFastWithDedicatedException() {
        ListableBeanFactory emptyFactory = new org.springframework.beans.factory.support.StaticListableBeanFactory();
        HandlerRegistry registry = HandlerRegistry.from(emptyFactory);
        HandlerResolver resolver = new RegistryHandlerResolver(registry);
        CommandDispatcher dispatcher = new CommandDispatcher(resolver,
                new CompositeCommandPolicyResolver(List.of()), new CommandPipeline(List.of()));

        assertThatThrownBy(() -> dispatcher.dispatch(new PingCommand("x")))
                .isInstanceOf(MissingCommandHandlerException.class)
                .hasMessageContaining(PingCommand.class.getName());
    }

    @Test
    void duplicateCommandHandler_failsFastAtRegistryBuild() {
        ListableBeanFactory factory = new AnnotationConfigApplicationContext(TwoPingCfg.class);
        assertThatThrownBy(() -> HandlerRegistry.from(factory))
                .isInstanceOf(DuplicateCommandHandlerException.class)
                .hasMessageContaining(PingCommand.class.getName());
    }

    @Configuration
    static class TwoPingCfg {
        @Bean
        PingHandler a() {
            return new PingHandler();
        }

        @Bean
        PingHandler b() {
            return new PingHandler();
        }
    }
}
