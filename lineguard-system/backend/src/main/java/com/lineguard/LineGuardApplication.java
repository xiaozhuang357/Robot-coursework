package com.lineguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LineGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(LineGuardApplication.class, args);
    }
}

