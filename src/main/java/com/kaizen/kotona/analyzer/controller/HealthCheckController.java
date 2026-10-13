package com.kaizen.kotona.analyzer.controller;

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
                // 버전을 적지 않는다. "Spring Boot 3" 이라고 적힌 채로 4 로 올라간 적이 있다.
                "message", "KOTONA Backend is running",
                "serverTime", LocalDateTime.now().toString()
        );
    }
}
