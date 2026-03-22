package com.passtheo.content.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configures the WebClient for user-service internal API calls.
 */
@Configuration
public class UserServiceClientConfig {

    @Value("${passtheo.user-service.base-url:http://localhost:8082}")
    private String userServiceBaseUrl;

    /**
     * Creates a WebClient pre-configured for user-service internal API calls.
     *
     * @return the user-service WebClient
     */
    @Bean("contentUserServiceWebClient")
    public WebClient contentUserServiceWebClient() {
        return WebClient.builder()
                .baseUrl(userServiceBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }
}
