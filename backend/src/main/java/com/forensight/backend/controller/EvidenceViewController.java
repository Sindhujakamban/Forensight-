package com.forensight.backend.controller;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.forensight.backend.model.Evidence;
import com.forensight.backend.repository.EvidenceRepository;
import com.forensight.backend.service.EvidenceFileStorageService;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceViewController {
    private final EvidenceRepository evidenceRepository;
    private final EvidenceFileStorageService fileStorage;

    public EvidenceViewController(EvidenceRepository evidenceRepository,
            EvidenceFileStorageService fileStorage) {
        this.evidenceRepository = evidenceRepository;
        this.fileStorage = fileStorage;
    }

    @GetMapping("/metadata/{evidenceId}")
    public ResponseEntity<Map<String, String>> getMetadata(@PathVariable String evidenceId) {
        Optional<Evidence> found = evidenceRepository.findById(evidenceId);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        Map<String, String> metadata = found.get().getMetadata();
        return ResponseEntity.ok(metadata == null ? Collections.emptyMap() : metadata);
    }

    @GetMapping("/original/{evidenceId}")
    public ResponseEntity<byte[]> getOriginalImage(@PathVariable String evidenceId) {
        Optional<Evidence> found = evidenceRepository.findById(evidenceId);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        return imageResponse(found.get().getFilePath(), MediaType.parseMediaType(found.get().getFileType()));
    }

    @GetMapping("/ela/{evidenceId}")
    public ResponseEntity<byte[]> getElaImage(@PathVariable String evidenceId) {
        Optional<Evidence> found = evidenceRepository.findById(evidenceId);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        return imageResponse(found.get().getElaPath(), MediaType.IMAGE_JPEG);
    }

    @GetMapping("/trufor/{evidenceId}")
    public ResponseEntity<byte[]> getTruForHeatmap(@PathVariable String evidenceId) {
        Optional<Evidence> found = evidenceRepository.findById(evidenceId);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        return imageResponse(found.get().getTruforPath(), MediaType.IMAGE_PNG);
    }

    @GetMapping("/trufor-confidence/{evidenceId}")
    public ResponseEntity<byte[]> getTruForConfidenceMap(@PathVariable String evidenceId) {
        Optional<Evidence> found = evidenceRepository.findById(evidenceId);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        return imageResponse(found.get().getTruforConfidencePath(), MediaType.IMAGE_PNG);
    }

    private ResponseEntity<byte[]> imageResponse(String reference, MediaType mediaType) {
        if (reference == null || reference.isBlank()) return ResponseEntity.notFound().build();
        try {
            byte[] bytes = fileStorage.readAllBytes(reference);
            return ResponseEntity.ok().contentType(mediaType).contentLength(bytes.length).body(bytes);
        } catch (IOException exception) {
            return ResponseEntity.notFound().build();
        }
    }
}
