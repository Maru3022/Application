package com.healthlife.auth.config;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация auth-service.
 * GoogleAuthenticator вынесен в Spring-бин для тестируемости
 * (можно мокировать в unit-тестах без статических вызовов).
 */
@Configuration
public class AuthConfig {

    @Bean
    public GoogleAuthenticator googleAuthenticator() {
        return new GoogleAuthenticator();
    }
}
