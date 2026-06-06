package com.healthlife.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Общая конфигурация Spring Security для всех микросервисов.
 *
 * <p>Применяется через common-security shared module. Каждый сервис наследует эти настройки.
 *
 * <p>Ключевые решения:
 * <ul>
 *   <li>CSRF отключён — REST API использует stateless JWT, CSRF не применим</li>
 *   <li>Сессии: STATELESS — токены в заголовке Authorization, не в cookie</li>
 *   <li>BCrypt strength=12 — выше дефолтного 10, баланс безопасности и производительности</li>
 *   <li>Swagger UI закрыт в production (профиль "production") — открыт только в dev</li>
 *   <li>Actuator /prometheus и /metrics требуют аутентификации</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class DefaultSecurityConfig {

    /**
     * Spring Environment для проверки активных профилей.
     * Использует Environment вместо @Value("${spring.profiles.active}") —
     * безопасно при множественных профилях и в тестах (Boot 3.5+).
     */
    private final Environment environment;

    public DefaultSecurityConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * BCrypt с cost factor 12.
     * Выше дефолтного 10 — более устойчив к брутфорсу при разумном времени хэширования (~300ms).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {

        http
                // CSRF отключён — stateless REST API с JWT в заголовке, не в cookie
                .csrf(AbstractHttpConfigurer::disable)

                // CORS настраивается через CorsConfig bean (читает cors.allowed-origins из конфига)
                .cors(cors -> cors.configure(http))

                // Заголовки безопасности
                .headers(headers -> headers
                        // Запрет встраивания в iframe — защита от clickjacking
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        // HSTS — принудительный HTTPS на 1 год, включая поддомены
                        .httpStrictTransportSecurity(
                                hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        // XSS Protection для старых браузеров
                        .xssProtection(
                                xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        // Запрет MIME-sniffing — X-Content-Type-Options: nosniff
                        .contentTypeOptions(contentTypeOptions -> {}) // включён по умолчанию в Spring Security
                        // Content Security Policy — только собственные ресурсы
                        .contentSecurityPolicy(
                                csp -> csp.policyDirectives("default-src 'self'; " + "frame-ancestors 'none'; "
                                        + "script-src 'self'; "
                                        + "style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; "
                                        + "connect-src 'self'")))

                // Stateless сессии — JWT в каждом запросе
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Правила доступа к эндпоинтам
                .authorizeHttpRequests(auth -> {
                    // Публичные auth эндпоинты (регистрация, логин, сброс пароля)
                    auth.requestMatchers("/api/v1/auth/**").permitAll();

                    // Stripe webhook — без JWT (проверка подписи Stripe в payment-service)
                    auth.requestMatchers("/api/v1/payments/webhook").permitAll();

                    // K8s health probes — публичный доступ только к health/info (базовый путь /internal/actuator)
                    auth.requestMatchers(
                                    "/internal/actuator/health",
                                    "/internal/actuator/health/liveness",
                                    "/internal/actuator/health/readiness",
                                    "/internal/actuator/info",
                                    // Обратная совместимость на период миграции
                                    "/actuator/health",
                                    "/actuator/health/liveness",
                                    "/actuator/health/readiness",
                                    "/actuator/info")
                            .permitAll();

                    // Остальные actuator эндпоинты закрыты для внешних запросов
                    auth.requestMatchers("/internal/actuator/**", "/actuator/**")
                            .authenticated();

                    // Swagger UI — открыт только в dev/test профилях
                    // В production закрыт для предотвращения утечки API схемы
                    if (!isProductionProfile()) {
                        auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs")
                                .permitAll();
                    } else {
                        auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs")
                                .authenticated();
                    }

                    // Все остальные запросы требуют аутентификации
                    auth.anyRequest().authenticated();
                })

                // JWT фильтр — проверяет токен перед стандартной аутентификацией
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Проверяет, запущен ли сервис в production профиле.
     * Использует Environment.getActiveProfiles() — корректно работает при множественных профилях.
     */
    private boolean isProductionProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if (profile.contains("production") || profile.contains("prod")) {
                return true;
            }
        }
        return false;
    }
}
