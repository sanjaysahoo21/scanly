package com.scanly.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Health Check Controller.
 *
 * A simple endpoint to verify the API is running.
 * This is the very first endpoint in our backend — test it with:
 *
 *   curl http://localhost:8080/api/v1/health
 *
 * Expected response:
 *   { "status": "UP", "service": "scanly", "timestamp": "2026-08-10T..." }
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "scanly",
            "timestamp", Instant.now().toString()
        ));
    }
}
