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
 * Preserves the original ForenSight scoring formula:
 * ELA 40% + metadata availability score 20% + TruFor authenticity 40%.
 *
 * When the optional TruFor model is not installed on the hosting service,
 * the service returns a clearly marked preliminary score using only the
 * available ELA and metadata signals. The two available weights are
 * renormalized to 100%; this is not represented as the full TruFor result.
 */
@Service
public class AnalysisService {

    private static final double ELA_WEIGHT = 0.40;
    private static final double METADATA_WEIGHT = 0.20;
    private static final double TRUFOR_WEIGHT = 0.40;
    private static final double AVAILABLE_WITHOUT_TRUFOR = ELA_WEIGHT + METADATA_WEIGHT;

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
            Map<String, String> metadata = metadataService.extractMetadata(evidence.getFilePath());
            evidence.setMetadata(metadata);

            // The original implementation runs ELA for JPEG and PNG files.
            // PNG is recompressed to JPEG for comparison; this is screening,
            // not proof of the image's original compression history.
            ElaAnalysisService.ElaResult ela = elaAnalysisService.analyze(evidence.getFilePath());
            boolean elaSupported = ela != null
                    && !"UNSUPPORTED".equalsIgnoreCase(ela.getResult())
                    && !"ERROR".equalsIgnoreCase(ela.getResult());
            if (!elaSupported) {
                throw new IOException("ELA could not produce a usable screening result.");
            }

            String elaImagePath = elaAnalysisService.generateElaImage(evidence.getFilePath(), evidenceId);
            evidence.setElaPath(elaImagePath);

            // Never reuse visual artifacts from an older run.
            evidence.setTruforPath(null);
            evidence.setTruforConfidencePath(null);
            evidenceRepository.save(evidence);

            double elaScore = clampPercent(ela.getConfidence() * 100.0);
            double metadataScore = metadata == null || metadata.isEmpty()
                    || metadata.containsKey("Error") ? 60.0 : 100.0;

            TruForService.TruForResult truFor = null;
            String truForStatus;
            if (!truForService.isEnabled()) {
                truForStatus = "TruFor is disabled on this hosting service. Its original 40% component is unavailable; this run is marked preliminary.";
            } else {
                try {
                    Optional<TruForService.TruForResult> optional =
                            truForService.analyzeIfConfigured(evidence.getFilePath(), evidenceId);
                    if (optional.isPresent()) {
                        truFor = optional.get();
                        evidence.setTruforPath(truFor.getHeatmapPath());
                        evidence.setTruforConfidencePath(truFor.getConfidenceMapPath());
                        evidenceRepository.save(evidence);
                        truForStatus = "TruFor executed successfully. Native localization heatmap and confidence map were saved.";
                    } else {
                        truForStatus = "TruFor did not run. Its original 40% component is unavailable; this run is marked preliminary.";
                    }
                } catch (IOException truForException) {
                    // An unavailable optional model must not prevent the original
                    // ELA + metadata screening from returning a useful result.
                    truForStatus = "TruFor inference failed on the hosting service. Check the model files, bridge path, and Python dependencies; this run is marked preliminary.";
                }
            }

            double finalScore;
            String analysisMethod;
            boolean truForAvailable = truFor != null;
            Double truForAuthenticity = null;
            Double truForConfidence = null;
            String finalResult;
            String riskLevel;
            String explanation;

            if (truForAvailable) {
                // Original TruFor convention: 0 = low manipulation evidence,
                // 1 = high manipulation evidence. Convert to authenticity.
                truForAuthenticity = clampPercent((1.0 - truFor.getScore()) * 100.0);
                truForConfidence = truFor.getConfidence();

                finalScore = (elaScore * ELA_WEIGHT)
                        + (metadataScore * METADATA_WEIGHT)
                        + (truForAuthenticity * TRUFOR_WEIGHT);
                analysisMethod = "LEGACY_40_20_40";
                explanation = String.format(
                        java.util.Locale.ROOT,
                        "Original ForenSight combined forensic calculation. ELA contributes 40%% (%.2f%%), metadata availability contributes 20%% (%.2f%%), and TruFor authenticity contributes 40%% (%.2f%%). Final authenticity score: %.2f%%. Risk level: %s. TruFor model confidence: %.2f%%. Metadata presence is a heuristic signal, not proof of authenticity; automated screening is an analytical aid, not standalone proof.",
                        elaScore, metadataScore, truForAuthenticity, finalScore, riskLevelFor(finalScore),
                        truFor.getConfidence() * 100.0);
            } else {
                // Preserve the original relative ELA:metadata ratio (40:20)
                // while explicitly normalizing across only the 60% of signals
                // actually available. Do not invent the missing TruFor score.
                finalScore = ((elaScore * ELA_WEIGHT) + (metadataScore * METADATA_WEIGHT))
                        / AVAILABLE_WITHOUT_TRUFOR;
                analysisMethod = "LEGACY_40_20_40_PRELIMINARY_NORMALIZED";
                explanation = String.format(
                        java.util.Locale.ROOT,
                        "PRELIMINARY RESULT — the original ForenSight formula is ELA 40%% + metadata availability 20%% + TruFor authenticity 40%%. TruFor was unavailable on this host, so no TruFor value has been invented. The displayed preliminary score (%.2f%%) is the weighted ELA (%.2f%%) and metadata (%.2f%%) result normalized across the available 60%% only. It is not the complete original 40/20/40 combined result. Metadata presence is a heuristic signal, not proof of authenticity. %s",
                        finalScore, elaScore, metadataScore, truForStatus);
            }

            finalResult = finalScore >= 70.0 ? "AUTHENTIC" : "MANIPULATED";
            riskLevel = riskLevelFor(finalScore);

            String elaFinding = "SUSPICIOUS".equalsIgnoreCase(ela.getResult())
                    || "LIKELY_MANIPULATED".equalsIgnoreCase(ela.getResult())
                    ? "POSSIBLE_COMPRESSION_ANOMALY"
                    : "NO_CLEAR_COMPRESSION_ANOMALY";

            AnalysisResult result = new AnalysisResult();
            result.setEvidenceId(evidenceId);
            result.setResult(finalResult);
            result.setConfidence(finalScore / 100.0);
            result.setExplanation(explanation);
            result.setAnalyzedAt(LocalDateTime.now());
            result.setElaScore(elaScore);
            result.setElaFinding(elaFinding);
            result.setMetadataScore(metadataScore);
            result.setFinalScore(finalScore);
            result.setRiskLevel(riskLevel);
            result.setElaSupported(true);
            result.setAnalysisMethod(analysisMethod);
            result.setTruForAvailable(truForAvailable);
            result.setTruForAuthenticity(truForAuthenticity);
            result.setTruForConfidence(truForConfidence);
            result.setTruForStatus(truForStatus);
            return analysisResultRepository.save(result);

        } catch (IOException exception) {
            evidence.setTruforPath(null);
            evidence.setTruforConfidencePath(null);
            evidenceRepository.save(evidence);

            AnalysisResult failed = new AnalysisResult();
            failed.setEvidenceId(evidenceId);
            failed.setResult("ERROR");
            failed.setConfidence(0.0);
            failed.setExplanation("Image screening could not be completed: " + exception.getMessage());
            failed.setElaScore(null);
            failed.setElaFinding("ERROR");
            failed.setMetadataScore(null);
            failed.setFinalScore(null);
            failed.setRiskLevel("UNKNOWN");
            failed.setElaSupported(false);
            failed.setAnalysisMethod("FAILED");
            failed.setTruForAvailable(false);
            failed.setTruForStatus("No complete ELA screening result was created.");
            failed.setAnalyzedAt(LocalDateTime.now());
            return analysisResultRepository.save(failed);
        }
    }

    private static String riskLevelFor(double score) {
        if (score >= 80.0) return "LOW";
        if (score >= 50.0) return "MEDIUM";
        return "HIGH";
    }

    private static double clampPercent(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    public Optional<AnalysisResult> getAnalysis(String evidenceId) {
        return analysisResultRepository.findByEvidenceIdOrderByAnalyzedAtDesc(evidenceId)
                .stream().findFirst();
    }
}
