package com.healthlife.healthdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.healthlife.healthdata", "com.healthlife.common"})
public class HealthDataServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(HealthDataServiceApplication.class, args);
    }
}
