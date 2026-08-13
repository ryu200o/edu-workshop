package io.github.ryu200o.eduworkshop.attendance.internal.adapter.bootstrap;

import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.parameter.AttendanceReconciliationParameters;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link AttendanceReconciliationProperties} and publishes it as an
 * {@link AttendanceReconciliationParameters} POJO for constructor injection into application
 * handlers/event handlers — keeps the Operational Policy out of the domain (ADR 0019 §4).
 */
@Configuration
@EnableConfigurationProperties(AttendanceReconciliationProperties.class)
class AttendanceReconciliationBootstrapConfig {

    @Bean
    public AttendanceReconciliationParameters attendanceReconciliationParameters(AttendanceReconciliationProperties props) {
        return new AttendanceReconciliationParameters(props.windowMinutes());
    }
}