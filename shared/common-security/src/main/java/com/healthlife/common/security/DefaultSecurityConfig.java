package com.healthlife.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

@Configuration
@EnableWebSecurity
public class DefaultSecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configure(http))
                .headers(headers -> headers.contentSecurityPolicy(
                                csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'"))
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .httpStrictTransportSecurity(
                                hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .xssProtection(
                                xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/v1/auth/**")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/actuator/**")
                        .authenticated()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
