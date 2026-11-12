package com.forensight.backend.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.forensight.backend.model.AnalysisResult;
import com.forensight.backend.model.Evidence;
import com.forensight.backend.repository.AnalysisResultRepository;
import com.forensight.backend.repository.EvidenceRepository;

/**
 * Restored ForenSight scoring pipeline documented in the user's 18-Aug-2026
 * forensic report: TruFor is the primary signal (75%) and ELA is supporting
 * evidence (25%). Metadata is retained as context and is not scored.
 */
@Service
public class AnalysisService {

    private static final double TRUFOR_WEIGHT = 0.75;
    private static final double ELA_WEIGHT = 0.25;

    private final AnalysisResultRepository analysisResultRepository;
    private final EvidenceRepository evidenceRepository;
    private final ElaAnalysisService elaAnalysisService;
    private final MetadataService metadataService;
    private final TruForService truForService;

    public AnalysisService(AnalysisResultRepository analysisResultRepository,
            EvidenceRepository evidenceRepository,
            ElaAnalysisService elaAnalysisService,
            MetadataService metadataService,
            TruForService truForService) {
        this.analysisResultRepository = analysisResultRepository;
        this.evidenceRepository = evidenceRepository;
        this.elaAnalysisService = elaAnalysisService;
        this.metadataService = metadataService;
        this.truForService = truForService;
    }

    public AnalysisResult analyzeEvidence(String evidenceId) {
        Evidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("Evidence not found."));

        try {
            // Metadata is reported as descriptive forensic context only.
            Map<String, String> metadata = metadataService.extractMetadata(evidence.getFilePath());
            evidence.setMetadata(metadata);

            ElaAnalysisService.ElaResult ela = elaAnalysisService.analyze(evidence.getFilePath());
            boolean elaSupported = ela != null
                    && !"UNSUPPORTED".equalsIgnoreCase(ela.getResult())
                    && !"ERROR".equalsIgnoreCase(ela.getResult());

            String elaImagePath = null;
            if (elaSupported) {
                elaImagePath = elaAnalysisService.generateElaImage(evidence.getFilePath(), evidenceId);
            }
            evidence.setElaPath(elaImagePath);

            // Clear old paths before running; a failed run must not show stale maps.
            evidence.setTruforPath(null);
            evidence.setTruforConfidencePath(null);
            evidenceRepository.save(evidence);

            Optional<TruForService.TruForResult> optional =
                    truForService.analyzeIfConfigured(evidence.getFilePath(), evidenceId);
            if (optional.isEmpty()) {
                throw new IOException("TruFor is disabled. No original combined calculation was created.");
            }
            TruForService.TruForResult truFor = optional.get();

            // Persist actual images exported from this run's native TruFor NPZ.
            evidence.setTruforPath(truFor.getHeatmapPath());
            evidence.setTruforConfidencePath(truFor.getConfidenceMapPath());
            evidenceRepository.save(evidence);

            double nativeTruForScore = truFor.getScore(); // 0..1; higher means more manipulation signal
            double truForAuthenticity = (1.0 - nativeTruForScore) * 100.0;
            Double elaScore = elaSupported ? ela.getConfidence() * 100.0 : null;

            // Match the previous report. If ELA cannot run for a format, use
            // TruFor alone and state that the 75/25 combined method was not used.
            double finalAuthenticityScore;
            String analysisMethod;
            if (elaSupported) {
                finalAuthenticityScore = (truForAuthenticity * TRUFOR_WEIGHT)
                        + (elaScore * ELA_WEIGHT);
                analysisMethod = "TRUFOR_75_ELA_25";
            } else {
                finalAuthenticityScore = truForAuthenticity;
                analysisMethod = "TRUFOR_ONLY_ELA_UNAVAILABLE";
            }

            // Keep the same 80/50 risk bands present in the archived backend.
            String riskLevel;
            if (finalAuthenticityScore >= 80.0) {
                riskLevel = "LOW";
            } else if (finalAuthenticityScore >= 50.0) {
                riskLevel = "MEDIUM";
            } else {
                riskLevel = "HIGH";
            }

            // Match the user's historical ForenSight reports: valid combined
            // authenticity scores below 80 are labelled MANIPULATED; scores
            // at or above 80 are labelled AUTHENTIC. Risk remains independent
            // and retains the original 80/50 LOW/MEDIUM/HIGH bands.
            // This classification boundary is inferred from the two supplied
            // historical reports (21.57% and 68.04%, both MANIPULATED).
            String finalResult = finalAuthenticityScore < 80.0
                    ? "MANIPULATED"
                    : "AUTHENTIC";

            String elaFinding;
            if (!elaSupported) {
                elaFinding = "NOT_SUPPORTED";
            } else if ("SUSPICIOUS".equalsIgnoreCase(ela.getResult())
                    || "LIKELY_MANIPULATED".equalsIgnoreCase(ela.getResult())) {
                elaFinding = "POSSIBLE_COMPRESSION_ANOMALY";
            } else {
                elaFinding = "NO_CLEAR_COMPRESSION_ANOMALY";
            }

            String explanation;
            if (elaSupported) {
                explanation = String.format(
                        java.util.Locale.ROOT,
                        "Forensic assessment using TruFor AI as the primary signal with ELA as supporting evidence. "
                        + "TruFor authenticity score: %.2f%%. ELA supporting score: %.2f%%. "
                        + "TruFor contributes 75%% of the final score, while ELA contributes 25%%. "
                        + "Metadata is reported as forensic context and is not used directly in the authenticity score. "
                        + "Final authenticity score: %.2f%%. Risk level: %s. "
                        + "TruFor model confidence: %.2f%%. This automated screening is an analytical aid, not standalone proof.",
                        truForAuthenticity, elaScore, finalAuthenticityScore, riskLevel,
                        truFor.getConfidence() * 100.0);
            } else {
                explanation = String.format(
                        java.util.Locale.ROOT,
                        "Forensic assessment using TruFor AI. ELA is not supported for this image format, "
                        + "so the displayed authenticity score uses TruFor alone; the 75/25 combined formula was not applied. "
                        + "TruFor authenticity score: %.2f%%. Metadata is reported as context only. "
                        + "Final authenticity score: %.2f%%. Risk level: %s. TruFor model confidence: %.2f%%. "
                        + "This automated screening is an analytical aid, not standalone proof.",
                        truForAuthenticity, finalAuthenticityScore, riskLevel,
                        truFor.getConfidence() * 100.0);
            }

            AnalysisResult result = new AnalysisResult();
            result.setEvidenceId(evidenceId);
            result.setResult(finalResult);
            // The existing report/PDF contract displays confidence as the final authenticity score.
            result.setConfidence(finalAuthenticityScore / 100.0);
            result.setExplanation(explanation);
            result.setAnalyzedAt(LocalDateTime.now());
            result.setElaScore(elaScore);
            result.setElaFinding(elaFinding);
            result.setMetadataScore(null); // explicitly not a scored component in the original report
            result.setFinalScore(finalAuthenticityScore);
            result.setRiskLevel(riskLevel);
            result.setElaSupported(elaSupported);
            result.setAnalysisMethod(analysisMethod);
            result.setTruForAvailable(true);
            result.setTruForAuthenticity(truForAuthenticity);
            result.setTruForConfidence(truFor.getConfidence());
            result.setTruForStatus("TruFor executed successfully. Native localization heatmap and confidence map are saved.");

            return analysisResultRepository.save(result);
        } catch (IOException exception) {
            evidence.setTruforPath(null);
            evidence.setTruforConfidencePath(null);
            evidenceRepository.save(evidence);

            AnalysisResult failed = new AnalysisResult();
            failed.setEvidenceId(evidenceId);
            failed.setResult("ERROR");
            failed.setConfidence(0.0);
            failed.setExplanation("Unable to complete the TruFor + ELA forensic pipeline: "
                    + exception.getMessage());
            failed.setElaScore(null);
            failed.setElaFinding("ERROR");
            failed.setMetadataScore(null);
            failed.setFinalScore(null);
            failed.setRiskLevel("UNKNOWN");
            failed.setElaSupported(false);
            failed.setAnalysisMethod("FAILED");
            failed.setTruForAvailable(false);
            failed.setTruForStatus("TruFor did not produce a complete result and heatmap.");
            failed.setAnalyzedAt(LocalDateTime.now());
            return analysisResultRepository.save(failed);
        }
    }

    public Optional<AnalysisResult> getAnalysis(String evidenceId) {
        return analysisResultRepository.findByEvidenceIdOrderByAnalyzedAtDesc(evidenceId)
                .stream().findFirst();
    }
}
