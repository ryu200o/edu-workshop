package io.github.ryu200o.eduworkshop.workshop.internal.adapter.inbound.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduling infrastructure for the Workshop lifecycle scanner.
 *
 * <p>{@code @EnableScheduling} turns on the {@code @Scheduled} support globally; the
 * {@code @ConditionalOnProperty} guard ({@code app.workshop.lifecycle.enabled}) lets deployments
 * turn the scheduler off (e.g. integration tests or a future multi-instance setup) without code
 * changes — the scanner bean is skipped entirely when disabled.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.workshop.lifecycle.enabled", havingValue = "true", matchIfMissing = true)
class WorkshopSchedulingConfig {
}