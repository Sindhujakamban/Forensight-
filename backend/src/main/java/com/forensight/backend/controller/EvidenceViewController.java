package com.forensight.backend.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.forensight.backend.model.Evidence;
import com.forensight.backend.repository.EvidenceRepository;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceViewController {

    private final EvidenceRepository evidenceRepository;

    public EvidenceViewController(EvidenceRepository evidenceRepository) {
        this.evidenceRepository = evidenceRepository;
    }

    @GetMapping("/metadata/{evidenceId}")
    public ResponseEntity<Map<String, String>> getMetadata(@PathVariable String evidenceId) {
        Optional<Evidence> found = evidenceRepository.findById(evidenceId);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        Map<String, String> metadata = found.get().getMetadata();
        return ResponseEntity.ok(metadata == null ? Collections.emptyMap() : metadata);
    }

    @GetMapping("/original/{evidenceId}")
    public ResponseEntity<Resource> getOriginalImage(@PathVariable String evidenceId) {
        Optional<Evidence> found = evidenceRepository.findById(evidenceId);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        return imageResponse(found.get().getFilePath());
    }

    @GetMapping("/ela/{evidenceId}")
    public ResponseEntity<Resource> getElaImage(@PathVariable String evidenceId) {
        Optional<Evidence> found = evidenceRepository.findById(evidenceId);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        return imageResponse(found.get().getElaPath());
    }

    @GetMapping("/trufor/{evidenceId}")
    public ResponseEntity<Resource> getTruForHeatmap(@PathVariable String evidenceId) {
        Optional<Evidence> found = evidenceRepository.findById(evidenceId);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        return imageResponse(found.get().getTruforPath());
    }

    @GetMapping("/trufor-confidence/{evidenceId}")
    public ResponseEntity<Resource> getTruForConfidenceMap(@PathVariable String evidenceId) {
        Optional<Evidence> found = evidenceRepository.findById(evidenceId);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        return imageResponse(found.get().getTruforConfidencePath());
    }

    private ResponseEntity<Resource> imageResponse(String path) {
        if (path == null || path.isBlank()) return ResponseEntity.notFound().build();
        File file = new File(path);
        if (!file.isFile() || !file.canRead()) return ResponseEntity.notFound().build();
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            String detected = Files.probeContentType(file.toPath());
            if (detected != null && detected.startsWith("image/")) {
                mediaType = MediaType.parseMediaType(detected);
            }
        } catch (IOException ignored) { }
        return ResponseEntity.ok().contentType(mediaType).body(new FileSystemResource(file));
    }
}
