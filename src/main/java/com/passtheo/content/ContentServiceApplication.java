package com.passtheo.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the PassTheo Content Service.
 * This is the core learning engine — practice sessions, mock exams,
 * spaced repetition, progress tracking, streaks, achievements, and study plans.
 */
@SpringBootApplication
public class ContentServiceApplication {

    /**
     * Starts the Content Service application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ContentServiceApplication.class, args);
    }
}
