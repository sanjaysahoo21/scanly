package com.scanly.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring's @Async annotation so DocumentProcessingService.processDocument()
 * runs in a background thread pool instead of blocking the upload HTTP response.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
