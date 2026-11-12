package com.forensight.backend.controller;

import com.forensight.backend.model.AnalysisResult;
import com.forensight.backend.service.AnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/{evidenceId}")
    public ResponseEntity<AnalysisResult> analyzeEvidence(
            @PathVariable String evidenceId) {

        return ResponseEntity.ok(
                analysisService.analyzeEvidence(evidenceId)
        );
    }

    @GetMapping("/{evidenceId}")
    public ResponseEntity<AnalysisResult> getAnalysis(
            @PathVariable String evidenceId) {

        return analysisService.getAnalysis(evidenceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}