package com.passtheo.content.config;

import com.passtheo.shared.security.filter.TenantFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web configuration — registers TenantFilter for API and internal endpoints.
 */
@Configuration
public class WebConfig {

    private final TenantFilter tenantFilter;

    /**
     * Constructs web configuration.
     *
     * @param tenantFilter the tenant isolation filter
     */
    public WebConfig(TenantFilter tenantFilter) {
        this.tenantFilter = tenantFilter;
    }

    /**
     * Registers the TenantFilter for API and internal URL patterns.
     *
     * @return the filter registration bean
     */
    @Bean
    public FilterRegistrationBean<TenantFilter> tenantFilterRegistration() {
        FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(tenantFilter);
        registration.addUrlPatterns("/api/*", "/internal/*");
        registration.setOrder(1);
        return registration;
    }
}
