package com.passtheo.content.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a Jackson 2.x ObjectMapper bean.
 * Spring Boot 4 auto-configures Jackson 3.x (tools.jackson), so we explicitly
 * expose the Jackson 2.x ObjectMapper for components that depend on it.
 */
@Configuration
public class JacksonConfig {

    /**
     * Creates the Jackson 2.x ObjectMapper bean.
     *
     * @return configured ObjectMapper with Java time support
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
