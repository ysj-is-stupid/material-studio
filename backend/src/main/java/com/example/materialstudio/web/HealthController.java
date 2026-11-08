package com.example.materialstudio.web;

import com.example.materialstudio.mapper.HealthMapper;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final HealthMapper healthMapper;

    public HealthController(HealthMapper healthMapper) {
        this.healthMapper = healthMapper;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        healthMapper.checkDatabase();
        return Map.of("status", "UP", "database", "UP");
    }
}
