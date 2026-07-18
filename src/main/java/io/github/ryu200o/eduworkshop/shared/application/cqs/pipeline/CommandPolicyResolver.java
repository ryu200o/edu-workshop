package io.github.ryu200o.eduworkshop.shared.application.cqs.pipeline;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Selects the {@link CommandPipeline} (ordered behavior chain) applicable to a given command. Resolution is
 * matcher-based: modules contribute {@link ModuleRegistration} beans (a {@code Predicate<Class<?>>} matcher +
 * pipeline) rather than being identified by package name.
 */
public interface CommandPolicyResolver {

    Optional<CommandPipeline> resolve(Command<?> command);

    /**
     * A module's contribution: a matcher over command types and the pipeline those commands should run
     * through.
     */
    record ModuleRegistration(Predicate<Class<?>> matcher, CommandPipeline pipeline) {
    }
}
