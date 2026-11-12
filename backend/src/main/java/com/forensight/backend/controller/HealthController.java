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

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "ForenSight AI Backend"
        ));
    }

    @GetMapping("/api/db-health")
    public ResponseEntity<Map<String, String>> databaseHealth() {
        try {
            mongoTemplate.getDb().runCommand(new Document("ping", 1));
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "database", "MongoDB Atlas"
            ));
        } catch (Exception exception) {
            // Return a non-2xx status so the frontend can correctly mark DB as unavailable.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "status", "DOWN",
                    "database", "MongoDB connection unavailable"
            ));
        }
    }
}
