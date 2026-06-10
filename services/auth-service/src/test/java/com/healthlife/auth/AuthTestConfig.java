package com.healthlife.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Test configuration for auth-service.
 *
 * <p>@EnableWebMvc registers the full MVC infrastructure including
 * HandlerMappingIntrospector (mvcHandlerMappingIntrospector), which is required
 * by Spring Security's WebSecurityConfiguration in Spring Boot 3.5+.
 * Without it, @SpringBootTest context fails with:
 *   "Injection of autowired dependencies failed" in WebSecurityConfiguration.
 *
 * <p>OAuth2 auto-configurations are excluded via application-test.yml
 * (spring.autoconfigure.exclude) to prevent missing client registration errors
 * from google-api-client transitive dependency.
 */
@TestConfiguration
@EnableWebMvc
public class AuthTestConfig {}
