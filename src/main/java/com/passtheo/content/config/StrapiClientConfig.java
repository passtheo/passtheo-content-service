package com.passtheo.content.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configures the WebClient for Strapi CMS REST API calls.
 */
@Configuration
public class StrapiClientConfig {

    @Value("${passtheo.strapi.base-url:http://localhost:1337}")
    private String strapiBaseUrl;

    @Value("${passtheo.strapi.api-token:}")
    private String strapiApiToken;

    /**
     * Creates a WebClient pre-configured for Strapi API calls.
     *
     * @return the Strapi WebClient
     */
    @Bean("strapiWebClient")
    public WebClient strapiWebClient(ObjectMapper objectMapper) {
        return WebClient.builder()
                .baseUrl(strapiBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + strapiApiToken)
                .codecs(configurer -> {
                    configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024);
                    configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper));
                    configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper));
                })
                .build();
    }
}
