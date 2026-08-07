package io.github.ryu200o.eduworkshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class EduWorkshopApplication {

    public static void main(String[] args) {
        SpringApplication.run(EduWorkshopApplication.class, args);
    }

}
