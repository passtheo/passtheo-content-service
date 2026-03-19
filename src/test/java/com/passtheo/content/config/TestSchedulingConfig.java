package com.passtheo.content.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Disables all @Scheduled tasks during acceptance tests to prevent side-effects.
 */
@TestConfiguration
public class TestSchedulingConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // No-op — prevents all scheduled tasks from registering during tests
    }
}
