package io.github.ryu200o.eduworkshop.shared.kernel.bus;

import io.github.ryu200o.eduworkshop.shared.cqs.Command;

import java.util.List;
import java.util.Optional;

/**
 * Default {@link CommandPolicyResolver}. Iterates the contributed {@link CommandPolicyResolver.ModuleRegistration}
 * beans in order and returns the pipeline of the first matcher that accepts the command type. If no matcher
 * applies, returns {@link Optional#empty()} so the dispatcher falls back to the default pass-through pipeline.
 */
public final class CompositeCommandPolicyResolver implements CommandPolicyResolver {

    private final List<ModuleRegistration> registrations;

    public CompositeCommandPolicyResolver(List<ModuleRegistration> registrations) {
        this.registrations = List.copyOf(registrations);
    }

    @Override
    public Optional<CommandPipeline> resolve(Command<?> command) {
        return registrations.stream()
                .filter(registration -> registration.matcher().test(command.getClass()))
                .findFirst()
                .map(ModuleRegistration::pipeline);
    }
}
