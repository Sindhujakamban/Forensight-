package com.forensight.backend.service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.forensight.backend.model.AnalysisResult;
import com.forensight.backend.model.Evidence;
import com.forensight.backend.repository.AnalysisResultRepository;
import com.forensight.backend.repository.EvidenceRepository;

/**
 * Original ForenSight 75/25 scoring: TruFor authenticity (75%) + ELA
 * supporting score (25%). Metadata is context only and is not scored.
 *
 * All durable image bytes live in MongoDB GridFS. Container-local files are
 * short-lived scratch inputs/outputs for metadata, ELA, and TruFor inference.
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
    private final EvidenceFileStorageService fileStorage;

    public AnalysisService(AnalysisResultRepository analysisResultRepository,
            EvidenceRepository evidenceRepository,
            ElaAnalysisService elaAnalysisService,
            MetadataService metadataService,
            TruForService truForService,
            EvidenceFileStorageService fileStorage) {
        this.analysisResultRepository = analysisResultRepository;
        this.evidenceRepository = evidenceRepository;
        this.elaAnalysisService = elaAnalysisService;
        this.metadataService = metadataService;
        this.truForService = truForService;
        this.fileStorage = fileStorage;
    }

    public AnalysisResult analyzeEvidence(String evidenceId) {
        Evidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("Evidence not found."));

        Path originalTemp = null;
        Path elaTemp = null;
        Path heatmapTemp = null;
        Path confidenceTemp = null;
        Path npzTemp = null;

        try {
            originalTemp = fileStorage.materialize(evidence.getFilePath(), evidence.getFileName());
            String originalPath = originalTemp.toString();

            Map<String, String> metadata = metadataService.extractMetadata(originalPath);
            evidence.setMetadata(metadata);

            // Remove old generated artefacts so a failed rerun cannot display stale maps.
            deleteOldArtifact(evidence.getElaPath());
            deleteOldArtifact(evidence.getTruforPath());
            deleteOldArtifact(evidence.getTruforConfidencePath());
            evidence.setElaPath(null);
            evidence.setTruforPath(null);
            evidence.setTruforConfidencePath(null);
            evidenceRepository.save(evidence);

            ElaAnalysisService.ElaResult ela = elaAnalysisService.analyze(originalPath);
            boolean elaSupported = ela != null
                    && !"UNSUPPORTED".equalsIgnoreCase(ela.getResult())
                    && !"ERROR".equalsIgnoreCase(ela.getResult());

            if (elaSupported) {
                elaTemp = Paths.get(elaAnalysisService.generateElaImage(originalPath, evidenceId));
                String elaReference = fileStorage.save(elaTemp,
                        evidenceId + "_ela.jpg", "image/jpeg");
                evidence.setElaPath(elaReference);
                evidenceRepository.save(evidence);
            }

            Optional<TruForService.TruForResult> optional =
                    truForService.analyzeIfConfigured(originalPath, evidenceId);
            if (optional.isEmpty()) {
                throw new IOException("TruFor is disabled. No original combined calculation was created.");
            }
            TruForService.TruForResult truFor = optional.get();

            heatmapTemp = Paths.get(truFor.getHeatmapPath());
            confidenceTemp = Paths.get(truFor.getConfidenceMapPath());
            npzTemp = Paths.get(truFor.getNpzPath());

            String heatmapReference = fileStorage.save(heatmapTemp,
                    evidenceId + "_trufor_heatmap.png", "image/png");
            String confidenceReference = fileStorage.save(confidenceTemp,
                    evidenceId + "_trufor_confidence.png", "image/png");

            evidence.setTruforPath(heatmapReference);
            evidence.setTruforConfidencePath(confidenceReference);
            evidenceRepository.save(evidence);

            double nativeTruForScore = truFor.getScore();
            double truForAuthenticity = (1.0 - nativeTruForScore) * 100.0;
            Double elaScore = elaSupported ? ela.getConfidence() * 100.0 : null;

            // Preserve the exact original behavior: for unsupported ELA formats,
            // TruFor alone is reported and the combined 75/25 formula is not claimed.
            double finalAuthenticityScore;
            String analysisMethod;
            if (elaSupported) {
                finalAuthenticityScore =
                        (truForAuthenticity * TRUFOR_WEIGHT) + (elaScore * ELA_WEIGHT);
                analysisMethod = "TRUFOR_75_ELA_25";
            } else {
                finalAuthenticityScore = truForAuthenticity;
                analysisMethod = "TRUFOR_ONLY_ELA_UNAVAILABLE";
            }

            String riskLevel;
            if (finalAuthenticityScore >= 80.0) riskLevel = "LOW";
            else if (finalAuthenticityScore >= 50.0) riskLevel = "MEDIUM";
            else riskLevel = "HIGH";

            String finalResult = finalAuthenticityScore < 80.0
                    ? "MANIPULATED" : "AUTHENTIC";

            String elaFinding;
            if (!elaSupported) elaFinding = "NOT_SUPPORTED";
            else if ("SUSPICIOUS".equalsIgnoreCase(ela.getResult())
                    || "LIKELY_MANIPULATED".equalsIgnoreCase(ela.getResult())) {
                elaFinding = "POSSIBLE_COMPRESSION_ANOMALY";
            } else {
                elaFinding = "NO_CLEAR_COMPRESSION_ANOMALY";
            }

            String explanation;
            if (elaSupported) {
                explanation = String.format(java.util.Locale.ROOT,
                        "Forensic assessment using TruFor AI as the primary signal with ELA as supporting evidence. "
                        + "TruFor authenticity score: %.2f%%. ELA supporting score: %.2f%%. "
                        + "TruFor contributes 75%% of the final score, while ELA contributes 25%%. "
                        + "Metadata is reported as forensic context and is not used directly in the authenticity score. "
                        + "Final authenticity score: %.2f%%. Risk level: %s. "
                        + "TruFor model confidence: %.2f%%. This automated screening is an analytical aid, not standalone proof.",
                        truForAuthenticity, elaScore, finalAuthenticityScore, riskLevel,
                        truFor.getConfidence() * 100.0);
            } else {
                explanation = String.format(java.util.Locale.ROOT,
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
            result.setConfidence(finalAuthenticityScore / 100.0);
            result.setExplanation(explanation);
            result.setAnalyzedAt(LocalDateTime.now());
            result.setElaScore(elaScore);
            result.setElaFinding(elaFinding);
            result.setMetadataScore(null);
            result.setFinalScore(finalAuthenticityScore);
            result.setRiskLevel(riskLevel);
            result.setElaSupported(elaSupported);
            result.setAnalysisMethod(analysisMethod);
            result.setTruForAvailable(true);
            result.setTruForAuthenticity(truForAuthenticity);
            result.setTruForConfidence(truFor.getConfidence());
            result.setTruForStatus("TruFor executed successfully. Native localization heatmap and confidence map were saved in GridFS. To fit the free Vercel memory limit, TruFor inference used a proportional copy capped at 768 pixels on the longest side; the original-resolution source image was retained for ELA and metadata.");
            return analysisResultRepository.save(result);

        } catch (IOException exception) {
            evidence.setElaPath(null);
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
        } finally {
            fileStorage.deleteTemporary(originalTemp);
            fileStorage.deleteTemporary(elaTemp);
            fileStorage.deleteTemporary(heatmapTemp);
            fileStorage.deleteTemporary(confidenceTemp);
            fileStorage.deleteTemporary(npzTemp);
        }
    }

    private void deleteOldArtifact(String reference) {
        if (reference == null || reference.isBlank()) return;
        try { fileStorage.delete(reference); }
        catch (IOException ignored) { /* A missing old artifact must not block a new run. */ }
    }

    public Optional<AnalysisResult> getAnalysis(String evidenceId) {
        return analysisResultRepository.findByEvidenceIdOrderByAnalyzedAtDesc(evidenceId)
                .stream().findFirst();
    }
}
