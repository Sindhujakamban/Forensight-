package com.forensight.backend.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "analysis_results")
public class AnalysisResult {

    @Id
    private String id;
    private String evidenceId;
    private String result;
    private double confidence;
    private String explanation;
    private LocalDateTime analyzedAt;

    // ELA / metadata context. elaScore is an ELA-derived authenticity indicator,
    // not a calibrated probability. metadataScore describes metadata availability only.
    private Double elaScore;
    private String elaFinding;
    private Double metadataScore;
    private Double finalScore;
    private String riskLevel;
    private boolean elaSupported;
    private String analysisMethod;

    // Optional TruFor output. Null when the model is not configured or unavailable.
    private boolean truForAvailable;
    private Double truForAuthenticity;
    private Double truForConfidence;
    private String truForStatus;

    public AnalysisResult() { }

    public AnalysisResult(String evidenceId, String result, double confidence,
            String explanation, LocalDateTime analyzedAt) {
        this.evidenceId = evidenceId;
        this.result = result;
        this.confidence = confidence;
        this.explanation = explanation;
        this.analyzedAt = analyzedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEvidenceId() { return evidenceId; }
    public void setEvidenceId(String evidenceId) { this.evidenceId = evidenceId; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }
    public Double getElaScore() { return elaScore; }
    public void setElaScore(Double elaScore) { this.elaScore = elaScore; }
    public String getElaFinding() { return elaFinding; }
    public void setElaFinding(String elaFinding) { this.elaFinding = elaFinding; }
    public Double getMetadataScore() { return metadataScore; }
    public void setMetadataScore(Double metadataScore) { this.metadataScore = metadataScore; }
    public Double getFinalScore() { return finalScore; }
    public void setFinalScore(Double finalScore) { this.finalScore = finalScore; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public boolean isElaSupported() { return elaSupported; }
    public void setElaSupported(boolean elaSupported) { this.elaSupported = elaSupported; }
    public String getAnalysisMethod() { return analysisMethod; }
    public void setAnalysisMethod(String analysisMethod) { this.analysisMethod = analysisMethod; }
    public boolean isTruForAvailable() { return truForAvailable; }
    public void setTruForAvailable(boolean truForAvailable) { this.truForAvailable = truForAvailable; }
    public Double getTruForAuthenticity() { return truForAuthenticity; }
    public void setTruForAuthenticity(Double truForAuthenticity) { this.truForAuthenticity = truForAuthenticity; }
    public Double getTruForConfidence() { return truForConfidence; }
    public void setTruForConfidence(Double truForConfidence) { this.truForConfidence = truForConfidence; }
    public String getTruForStatus() { return truForStatus; }
    public void setTruForStatus(String truForStatus) { this.truForStatus = truForStatus; }
}
