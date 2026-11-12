package com.forensight.backend.service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.forensight.backend.model.Case;
import com.forensight.backend.model.Evidence;
import com.forensight.backend.repository.CaseRepository;

@Service
public class CaseService {

    private final CaseRepository caseRepository;
    private final EvidenceService evidenceService;

    public CaseService(CaseRepository caseRepository, EvidenceService evidenceService) {
        this.caseRepository = caseRepository;
        this.evidenceService = evidenceService;
    }

    public Case createCase(Case caseData) {
        return caseRepository.save(caseData);
    }

    public List<Case> getAllCases() {
        return caseRepository.findAll();
    }

    public Optional<Case> getCaseById(String id) {
        return caseRepository.findById(id);
    }

    public void deleteCase(String id) {
        List<Evidence> evidenceItems = evidenceService.getEvidenceByCaseId(id);
        for (Evidence item : evidenceItems) {
            try {
                evidenceService.deleteEvidence(item.getId());
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Could not remove all files for evidence " + item.getId(), exception);
            }
        }
        caseRepository.deleteById(id);
    }
}
