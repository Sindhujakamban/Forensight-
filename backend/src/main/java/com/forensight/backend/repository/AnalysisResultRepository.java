package com.forensight.backend.repository;

import com.forensight.backend.model.AnalysisResult;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisResultRepository
        extends MongoRepository<AnalysisResult, String> {

    // Get all analysis results for an evidence,
    // newest analysis first
    List<AnalysisResult> findByEvidenceIdOrderByAnalyzedAtDesc(
            String evidenceId
    );

    // Get only the newest analysis result
    Optional<AnalysisResult> findFirstByEvidenceIdOrderByAnalyzedAtDesc(
            String evidenceId
    );

    void deleteByEvidenceId(String evidenceId);
}