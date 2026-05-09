package com.healthlife.auth;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

/**
 * Test configuration for auth-service.
 * Excludes OAuth2 auto-configurations that are pulled in transitively by google-api-client
 * (used by OAuthService for Google Sign-In token verification).
 */
@TestConfiguration
public class AuthTestConfig implements BeanDefinitionRegistryPostProcessor {

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (!registry.containsBeanDefinition("mvcHandlerMappingIntrospector")) {
            RootBeanDefinition beanDefinition = new RootBeanDefinition(HandlerMappingIntrospector.class);
            beanDefinition.setRole(org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE);
            registry.registerBeanDefinition("mvcHandlerMappingIntrospector", beanDefinition);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // no-op
    }
}
