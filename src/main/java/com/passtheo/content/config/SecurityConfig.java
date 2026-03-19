package com.passtheo.content.config;

import com.passtheo.shared.core.filter.TracingFilter;
import com.passtheo.shared.security.filter.TenantFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the Content Service.
 * Filter chain order: TenantFilter → TracingFilter → Application.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${passtheo.security.permit-all:false}")
    private boolean permitAll;

    /**
     * Creates the TenantFilter bean.
     *
     * @return the tenant isolation filter
     */
    @Bean
    public TenantFilter tenantFilter() {
        LOG.info("Creating TenantFilter bean");
        return new TenantFilter();
    }

    /**
     * Creates the TracingFilter bean.
     *
     * @return the distributed tracing filter
     */
    @Bean
    public TracingFilter tracingFilter() {
        LOG.info("Creating TracingFilter bean");
        return new TracingFilter();
    }

    /**
     * Configures the security filter chain.
     *
     * @param http the HttpSecurity builder
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    @Order(100)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(tenantFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(tracingFilter(), TenantFilter.class)
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/actuator/**").permitAll();
                auth.requestMatchers("/internal/**").permitAll();
                if (permitAll) {
                    LOG.info("Security: permit-all mode enabled (acceptance tests)");
                    auth.anyRequest().permitAll();
                } else {
                    auth.anyRequest().authenticated();
                }
            });
        return http.build();
    }
}
