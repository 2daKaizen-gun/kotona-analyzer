package com.kaizen.kotona.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class HealthCheckController {
    @GetMapping("/api/health")
    public Map<String, Object> healthCheck() {
        // JSON 응답
        return Map.of(
                "status", "up",
                "message", "KOTONA Backend is running on Spring Boot 3 & Java 21!",
                "serverTime", LocalDateTime.now().toString()
        );
    }
}
