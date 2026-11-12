package com.forensight.backend.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.forensight.backend.model.Evidence;
import com.forensight.backend.service.EvidenceService;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

    private final EvidenceService evidenceService;

    public EvidenceController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadEvidence(
            @RequestParam("caseId") String caseId,
            @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(evidenceService.uploadEvidence(caseId, file));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "message", exception.getMessage() == null ? "Invalid upload" : exception.getMessage()
            ));
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of(
                    "message", "The uploaded file could not be saved. Check the backend uploads folder."
            ));
        }
    }

    @GetMapping("/case/{caseId}")
    public ResponseEntity<List<Evidence>> getEvidenceByCaseId(@PathVariable String caseId) {
        return ResponseEntity.ok(evidenceService.getEvidenceByCaseId(caseId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEvidence(@PathVariable String id) {
        try {
            evidenceService.deleteEvidence(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        } catch (IOException exception) {
            return ResponseEntity.internalServerError().body(java.util.Map.of(
                    "message", "Evidence files could not be fully removed."
            ));
        }
    }
}
