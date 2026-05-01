package com.healthlife.aicoach;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.healthlife.aicoach", "com.healthlife.common"})
public class AiCoachServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiCoachServiceApplication.class, args);
    }
}
