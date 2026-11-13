package com.forensight.backend.controller;

import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final MongoTemplate mongoTemplate;

    public HealthController(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Readiness check used by Render and public uptime checks.
     * Return UP only when the API can reach MongoDB; a running JVM alone
     * is not enough for ForenSight to accept investigation requests.
     */
    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        try {
            mongoTemplate.getDb().runCommand(new Document("ping", 1));
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "service", "ForenSight AI Backend",
                    "database", "UP"
            ));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "status", "DOWN",
                    "service", "ForenSight AI Backend",
                    "database", "UNAVAILABLE"
            ));
        }
    }

    @GetMapping("/api/auth/check")
    public ResponseEntity<Void> authenticated() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/db-health")
    public ResponseEntity<Map<String, String>> databaseHealth() {
        try {
            mongoTemplate.getDb().runCommand(new Document("ping", 1));
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "database", "MongoDB"
            ));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "status", "DOWN",
                    "database", "MongoDB connection unavailable"
            ));
        }
    }
}
