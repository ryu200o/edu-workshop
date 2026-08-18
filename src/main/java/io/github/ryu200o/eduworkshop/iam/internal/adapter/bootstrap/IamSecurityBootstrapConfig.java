package io.github.ryu200o.eduworkshop.iam.internal.adapter.bootstrap;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.parameter.IamSecurityParameters;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link IamSecurityProperties} and publishes it as an {@link IamSecurityParameters} POJO for
 * constructor injection into the IAM handlers and security adapters — keeps the operational policy
 * out of the domain (same pattern as {@code WorkshopCheckInBootstrapConfig}).
 */
@Configuration
@EnableConfigurationProperties(IamSecurityProperties.class)
class IamSecurityBootstrapConfig {

    @Bean
    public IamSecurityParameters iamSecurityParameters(IamSecurityProperties props) {
        return new IamSecurityParameters(
                props.jwtSecret(),
                props.jwtAccessTtlMinutes(),
                props.jwtRefreshTtlDays(),
                props.otpTtlHours(),
                props.securityEnabled()
        );
    }
}
