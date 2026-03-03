package com.hms.gateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public health check endpoint — no JWT required.
 * Matches the same response format used by all HMS microservices.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "success", true,
                "data", Map.of("status", "ok", "service", "hms-gateway")
        );
    }
}
