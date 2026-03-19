package com.passtheo.content.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring scheduling for periodic jobs:
 * readiness snapshots, difficulty calibration, abandoned session cleanup.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
