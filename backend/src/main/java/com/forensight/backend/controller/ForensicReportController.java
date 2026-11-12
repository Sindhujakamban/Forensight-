package com.forensight.backend.controller;

import com.forensight.backend.model.AnalysisResult;
import com.forensight.backend.model.Evidence;
import com.forensight.backend.model.ForensicReport;

import com.forensight.backend.repository.AnalysisResultRepository;
import com.forensight.backend.repository.EvidenceRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;


@RestController
@RequestMapping("/api/report")
public class ForensicReportController {


    private final EvidenceRepository evidenceRepository;

    private final AnalysisResultRepository analysisResultRepository;


    // ==============================
    // CONSTRUCTOR
    // ==============================

    public ForensicReportController(
            EvidenceRepository evidenceRepository,
            AnalysisResultRepository analysisResultRepository) {

        this.evidenceRepository = evidenceRepository;
        this.analysisResultRepository = analysisResultRepository;
    }


    // ==============================
    // GET FORENSIC REPORT
    // ==============================

    @GetMapping("/{evidenceId}")
    public ResponseEntity<ForensicReport> getReport(
            @PathVariable String evidenceId) {


        // ==============================
        // FIND EVIDENCE
        // ==============================

        Optional<Evidence> evidenceOptional =
                evidenceRepository.findById(evidenceId);


        if (evidenceOptional.isEmpty()) {

            return ResponseEntity.notFound().build();
        }


        Evidence evidence = evidenceOptional.get();


        // ==============================
        // FIND ANALYSIS RESULTS
        // NEWEST FIRST
        // ==============================

        List<AnalysisResult> analyses =
                analysisResultRepository
                        .findByEvidenceIdOrderByAnalyzedAtDesc(
                                evidenceId
                        );


        if (analyses.isEmpty()) {

            return ResponseEntity.notFound().build();
        }


        // Get newest analysis
        AnalysisResult analysis = analyses.get(0);


        // ==============================
        // CREATE REPORT
        // ==============================

        ForensicReport report =
                new ForensicReport();


        // ==============================
        // EVIDENCE INFORMATION
        // ==============================

        report.setEvidenceId(
                evidence.getId()
        );


        report.setCaseId(
                evidence.getCaseId()
        );


        report.setFileName(
                evidence.getFileName()
        );


        report.setFileType(
                evidence.getFileType()
        );


        report.setFileSize(
                evidence.getFileSize()
        );


        // ==============================
        // IMAGE PATHS
        // ==============================

        report.setOriginalImage(
                evidence.getFilePath()
        );


        report.setElaImage(
                evidence.getElaPath()
        );
        report.setTruforImage(evidence.getTruforPath());
        report.setTruforConfidenceImage(evidence.getTruforConfidencePath());


        // ==============================
        // METADATA
        // ==============================

        report.setMetadata(
                evidence.getMetadata()
        );


        // ==============================
        // ANALYSIS RESULT
        // ==============================

        String reportResult = analysis.getResult();
        if (reportResult != null && reportResult.trim().equalsIgnoreCase("SUSPICIOUS")) {
            // Legacy V2 records used SUSPICIOUS for the same medium-risk
            // classification that the historical reports label MANIPULATED.
            reportResult = "MANIPULATED";
        } else if (reportResult != null && reportResult.trim().equalsIgnoreCase("LIKELY_MANIPULATED")) {
            reportResult = "MANIPULATED";
        } else if (reportResult != null && reportResult.trim().equalsIgnoreCase("LIKELY_AUTHENTIC")) {
            reportResult = "AUTHENTIC";
        }
        report.setResult(reportResult);


        report.setConfidence(
                analysis.getConfidence()
        );


        report.setExplanation(
                analysis.getExplanation()
        );


        // ==============================
        // FORENSIC SCORES
        // ==============================

        report.setElaScore(
                analysis.getElaScore()
        );
        report.setElaFinding(analysis.getElaFinding());


        report.setMetadataScore(
                analysis.getMetadataScore()
        );


        report.setFinalScore(
                analysis.getFinalScore()
        );


        report.setRiskLevel(analysis.getRiskLevel());
        report.setElaSupported(analysis.isElaSupported());
        report.setAnalysisMethod(analysis.getAnalysisMethod());
        report.setTruForAvailable(analysis.isTruForAvailable());
        report.setTruForAuthenticity(analysis.getTruForAuthenticity());
        report.setTruForConfidence(analysis.getTruForConfidence());
        report.setTruForStatus(analysis.getTruForStatus());


        // ==============================
        // RETURN REPORT
        // ==============================

        return ResponseEntity.ok(report);
    }
}