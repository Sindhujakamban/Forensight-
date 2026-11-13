package com.forensight.backend.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.forensight.backend.model.Evidence;
import com.forensight.backend.repository.AnalysisResultRepository;
import com.forensight.backend.repository.CaseRepository;
import com.forensight.backend.repository.EvidenceRepository;

@Service
public class EvidenceService {
    private final EvidenceRepository evidenceRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final CaseRepository caseRepository;
    private final EvidenceFileStorageService fileStorage;
    private final long maxUploadBytes;

    public EvidenceService(EvidenceRepository evidenceRepository,
            AnalysisResultRepository analysisResultRepository,
            CaseRepository caseRepository,
            EvidenceFileStorageService fileStorage,
            @Value("\${forensight.upload.max-bytes:4194304}") long maxUploadBytes) {
        this.evidenceRepository = evidenceRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.caseRepository = caseRepository;
        this.fileStorage = fileStorage;
        this.maxUploadBytes = maxUploadBytes;
    }

    public Evidence uploadEvidence(String caseId, MultipartFile file) throws IOException {
        if (caseId == null || caseId.isBlank() || !caseRepository.existsById(caseId)) {
            throw new IllegalArgumentException("Select a valid investigation before uploading evidence.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a non-empty image file.");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new IllegalArgumentException("Image exceeds the 4 MB upload limit for the free Vercel deployment.");
        }

        String originalName = file.getOriginalFilename() == null ? "evidence" : file.getOriginalFilename();
        String normalizedName = originalName.replace('\\', '/');
        String safeName = normalizedName.substring(normalizedName.lastIndexOf('/') + 1)
                .replaceAll("[^A-Za-z0-9._-]", "_");
        String lowerName = safeName.toLowerCase(Locale.ROOT);
        boolean jpeg = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg");
        boolean png = lowerName.endsWith(".png");
        if (!jpeg && !png) {
            throw new IllegalArgumentException("Only JPG, JPEG, and PNG images are supported.");
        }

        BufferedImage decoded;
        try (var input = file.getInputStream()) {
            decoded = ImageIO.read(input);
        }
        if (decoded == null) {
            throw new IllegalArgumentException("The selected file is not a readable JPG, JPEG, or PNG image.");
        }

        String contentType = jpeg ? "image/jpeg" : "image/png";
        String fileReference;
        try (var input = file.getInputStream()) {
            fileReference = fileStorage.save(input, UUID.randomUUID() + "_" + safeName, contentType);
        }

        Evidence evidence = new Evidence(caseId, safeName, contentType, file.getSize(),
                fileReference, LocalDateTime.now());
        try {
            return evidenceRepository.save(evidence);
        } catch (RuntimeException exception) {
            fileStorage.delete(fileReference);
            throw exception;
        }
    }

    public List<Evidence> getEvidenceByCaseId(String caseId) {
        return evidenceRepository.findByCaseId(caseId);
    }
    public Optional<Evidence> getEvidenceById(String evidenceId) {
        return evidenceRepository.findById(evidenceId);
    }

    public void deleteEvidence(String evidenceId) throws IOException {
        Evidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("Evidence not found."));
        fileStorage.delete(evidence.getFilePath());
        fileStorage.delete(evidence.getElaPath());
        fileStorage.delete(evidence.getTruforPath());
        fileStorage.delete(evidence.getTruforConfidencePath());
        analysisResultRepository.deleteByEvidenceId(evidenceId);
        evidenceRepository.deleteById(evidenceId);
    }
}
