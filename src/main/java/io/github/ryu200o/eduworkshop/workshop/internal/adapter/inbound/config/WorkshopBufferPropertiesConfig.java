package io.github.ryu200o.eduworkshop.workshop.internal.adapter.inbound.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link WorkshopBufferConfig} as a {@code @ConfigurationProperties} bean within the workshop
 * module boundary — avoids a cross-module type reference from the root application class (Modulith).
 */
@Configuration
@EnableConfigurationProperties(WorkshopBufferConfig.class)
class WorkshopBufferPropertiesConfig {
}
