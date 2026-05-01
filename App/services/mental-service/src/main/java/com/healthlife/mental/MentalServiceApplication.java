package com.healthlife.mental;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.healthlife.mental", "com.healthlife.common"})
public class MentalServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MentalServiceApplication.class, args);
    }
}
