package io.github.ryu200o.eduworkshop.workshop.internal.adapter.bootstrap;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.parameter.WorkshopBufferParameters;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link WorkshopBufferProperties} and publishes it as a {@link WorkshopBufferParameters} POJO
 * for constructor injection into command handlers — keeps the Operational Policy out of the domain.
 */
@Configuration
@EnableConfigurationProperties(WorkshopBufferProperties.class)
class WorkshopBufferBootstrapConfig {

    @Bean
    public WorkshopBufferParameters workshopBufferParameters(WorkshopBufferProperties props) {
        return new WorkshopBufferParameters(
                props.beforeDefaultMinutes(),
                props.maxMinutes()
        );
    }
}