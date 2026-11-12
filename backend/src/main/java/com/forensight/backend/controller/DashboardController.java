package com.forensight.backend.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.forensight.backend.model.AnalysisResult;
import com.forensight.backend.model.Evidence;
import com.forensight.backend.repository.AnalysisResultRepository;
import com.forensight.backend.repository.CaseRepository;
import com.forensight.backend.repository.EvidenceRepository;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final CaseRepository caseRepository;
    private final EvidenceRepository evidenceRepository;
    private final AnalysisResultRepository analysisResultRepository;

    public DashboardController(
            CaseRepository caseRepository,
            EvidenceRepository evidenceRepository,
            AnalysisResultRepository analysisResultRepository) {
        this.caseRepository = caseRepository;
        this.evidenceRepository = evidenceRepository;
        this.analysisResultRepository = analysisResultRepository;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getDashboardStats() {
        List<Evidence> evidenceList = evidenceRepository.findAll();

        long analyzedCount = 0;
        long authenticCount = 0;
        long manipulatedCount = 0;
        long highRiskCount = 0;

        for (Evidence evidence : evidenceList) {
            if (evidence.getId() == null) {
                continue;
            }

            Optional<AnalysisResult> latest = analysisResultRepository
                    .findFirstByEvidenceIdOrderByAnalyzedAtDesc(evidence.getId());

            if (latest.isEmpty()) {
                continue;
            }

            AnalysisResult analysis = latest.get();
            String result = analysis.getResult() == null
                    ? ""
                    : analysis.getResult().trim().toUpperCase();

            if (result.isBlank() || result.equals("ERROR") || result.equals("FAILED")
                    || result.equals("NOT ANALYZED")) {
                continue;
            }

            analyzedCount++;

            if (result.equals("LIKELY_AUTHENTIC") || result.equals("AUTHENTIC")) {
                authenticCount++;
            } else if (result.equals("SUSPICIOUS") || result.equals("LIKELY_MANIPULATED")
                    || result.equals("MANIPULATED")) {
                manipulatedCount++;
            }

            if ("HIGH".equalsIgnoreCase(analysis.getRiskLevel())) {
                highRiskCount++;
            }
        }

        Map<String, Long> stats = new HashMap<>();
        stats.put("totalCases", caseRepository.count());
        stats.put("totalEvidence", (long) evidenceList.size());
        stats.put("analyzedCount", analyzedCount);
        stats.put("authenticCount", authenticCount);
        stats.put("manipulatedCount", manipulatedCount);
        // Kept for backwards compatibility with any older UI code.
        stats.put("suspiciousCount", manipulatedCount);
        stats.put("highRiskCount", highRiskCount);

        return ResponseEntity.ok(stats);
    }
}
