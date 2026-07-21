package io.github.ryu200o.eduworkshop.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;

@Configuration
public class TimeConfig {

    @Bean
    @Primary
    public Clock clock() {
        return Clock.systemUTC();
    }
}
