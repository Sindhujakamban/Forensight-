package com.forensight.backend.controller;

import com.forensight.backend.model.Case;
import com.forensight.backend.service.CaseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    // Create a new case
    @PostMapping
    public ResponseEntity<Case> createCase(@RequestBody Case caseData) {
        return ResponseEntity.ok(caseService.createCase(caseData));
    }

    // Get all cases
    @GetMapping
    public ResponseEntity<List<Case>> getAllCases() {
        return ResponseEntity.ok(caseService.getAllCases());
    }

    // Get case by ID
    @GetMapping("/{id}")
    public ResponseEntity<Case> getCaseById(@PathVariable String id) {
        return caseService.getCaseById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Delete case
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCase(@PathVariable String id) {
        caseService.deleteCase(id);
        return ResponseEntity.noContent().build();
    }
}