package io.riwi.messaging.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.riwi.messaging")
public class RiwiMessagingApplication {
    public static void main(String[] args) {
        SpringApplication.run(RiwiMessagingApplication.class, args);
    }
}
