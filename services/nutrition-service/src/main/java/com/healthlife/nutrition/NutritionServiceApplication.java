package com.healthlife.nutrition;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.healthlife.nutrition", "com.healthlife.common"})
public class NutritionServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NutritionServiceApplication.class, args);
    }
}
