package com.insurancemegacorp.telematicsgen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TelematicsGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelematicsGeneratorApplication.class, args);
    }

}