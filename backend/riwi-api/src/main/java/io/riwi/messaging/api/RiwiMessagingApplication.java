package io.riwi.messaging.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "io.riwi.messaging")
@EnableScheduling
public class RiwiMessagingApplication {
    public static void main(String[] args) {
        SpringApplication.run(RiwiMessagingApplication.class, args);
    }
}
