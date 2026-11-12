package com.forensight.backend.model;

import java.util.Map;

public class ForensicReport {
    private String evidenceId;
    private String caseId;
    private String fileName;
    private String fileType;
    private long fileSize;
    private String originalImage;
    private String elaImage;
    private String truforImage;
    private String truforConfidenceImage;
    private String result;
    private double confidence;
    private String explanation;
    private Map<String, String> metadata;
    private Double elaScore;
    private String elaFinding;
    private Double metadataScore;
    private Double finalScore;
    private String riskLevel;
    private boolean elaSupported;
    private String analysisMethod;
    private boolean truForAvailable;
    private Double truForAuthenticity;
    private Double truForConfidence;
    private String truForStatus;

    public String getEvidenceId() { return evidenceId; }
    public void setEvidenceId(String evidenceId) { this.evidenceId = evidenceId; }
    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getOriginalImage() { return originalImage; }
    public void setOriginalImage(String originalImage) { this.originalImage = originalImage; }
    public String getElaImage() { return elaImage; }
    public void setElaImage(String elaImage) { this.elaImage = elaImage; }
    public String getTruforImage() { return truforImage; }
    public void setTruforImage(String truforImage) { this.truforImage = truforImage; }
    public String getTruforConfidenceImage() { return truforConfidenceImage; }
    public void setTruforConfidenceImage(String truforConfidenceImage) { this.truforConfidenceImage = truforConfidenceImage; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
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
