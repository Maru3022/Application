package com.healthlife.auth;

import org.springframework.boot.test.context.TestConfiguration;

/**
 * Test configuration for auth-service.
 *
 * <p>In Spring Boot 3.5 / Spring Security 6.5 the HandlerMappingIntrospector bean
 * (mvcHandlerMappingIntrospector) is always registered by the framework, so the
 * manual BeanDefinitionRegistryPostProcessor workaround that was needed in earlier
 * versions has been removed to avoid bean-definition conflicts.
 *
 * <p>Any test-only beans that need to be added in the future should go here.
 */
@TestConfiguration
public class AuthTestConfig {
    // intentionally empty – no overrides needed for Boot 3.5+
}
