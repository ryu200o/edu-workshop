package io.github.ryu200o.eduworkshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EduWorkshopApplication {

    public static void main(String[] args) {
        SpringApplication.run(EduWorkshopApplication.class, args);
    }

}
