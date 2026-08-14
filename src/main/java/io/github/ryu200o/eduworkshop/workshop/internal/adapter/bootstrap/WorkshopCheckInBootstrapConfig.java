package io.github.ryu200o.eduworkshop.workshop.internal.adapter.bootstrap;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.parameter.WorkshopCheckInParameters;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link WorkshopCheckInProperties} and publishes it as a {@link WorkshopCheckInParameters} POJO
 * for constructor injection into the Module Facade — keeps the Operational Policy out of the domain
 * (same pattern as {@link WorkshopBufferBootstrapConfig}).
 */
@Configuration
@EnableConfigurationProperties(WorkshopCheckInProperties.class)
class WorkshopCheckInBootstrapConfig {

    @Bean
    public WorkshopCheckInParameters workshopCheckInParameters(WorkshopCheckInProperties props) {
        return new WorkshopCheckInParameters(
                props.lateAfterMinutes()
        );
    }
}
