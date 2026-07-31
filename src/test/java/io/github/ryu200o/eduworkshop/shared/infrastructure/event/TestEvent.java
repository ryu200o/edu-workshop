package io.github.ryu200o.eduworkshop.shared.infrastructure.event;

import java.util.UUID;

/**
 * Test-only payload event used to exercise the Spring Modulith Event Publication
 * Registry (durability, failure recovery, restart replay).
 */
public record TestEvent(UUID id, String payload) {
}
