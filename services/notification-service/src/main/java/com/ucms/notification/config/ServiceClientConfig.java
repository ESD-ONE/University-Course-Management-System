package com.ucms.notification.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * The RestClient.Builder every client/ class is built from: calls go to a service name
 * (http://academic-structure-service), spread across its replicas by the load balancer,
 * with a failed GET retried once on another replica. Timeouts mean a hung service can't
 * hold this one's request threads indefinitely, and the request id travels along.
 * Prototype-scoped like Spring Boot's own builder, so each client gets a fresh one.
 */
@Configuration
public class ServiceClientConfig {

    @Bean
    @LoadBalanced
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public RestClient.Builder loadBalancedRestClientBuilder(
            RestClientBuilderConfigurer configurer,
            @Value("${ucms.http-client.connect-timeout:2s}") Duration connectTimeout,
            @Value("${ucms.http-client.read-timeout:10s}") Duration readTimeout) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout);
        return configurer.configure(RestClient.builder())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .requestInterceptor((request, body, execution) -> {
                    String requestId = MDC.get(RequestIdFilter.MDC_KEY);
                    if (requestId != null) request.getHeaders().set(RequestIdFilter.HEADER, requestId);
                    return execution.execute(request, body);
                });
    }
}
