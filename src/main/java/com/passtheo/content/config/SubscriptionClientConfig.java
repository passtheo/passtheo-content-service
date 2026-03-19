package com.passtheo.content.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configures the WebClient for subscription-service internal API calls.
 */
@Configuration
public class SubscriptionClientConfig {

    @Value("${passtheo.subscription-service.base-url:http://localhost:8083}")
    private String subscriptionServiceBaseUrl;

    /**
     * Creates a WebClient pre-configured for subscription-service calls.
     *
     * @return the subscription-service WebClient
     */
    @Bean("subscriptionWebClient")
    public WebClient subscriptionWebClient() {
        return WebClient.builder()
                .baseUrl(subscriptionServiceBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
