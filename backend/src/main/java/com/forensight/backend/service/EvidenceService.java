package com.forensight.backend.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import javax.imageio.ImageIO;

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
    private final Path uploadDirectory = Paths.get("uploads").toAbsolutePath().normalize();

    public EvidenceService(
            EvidenceRepository evidenceRepository,
            AnalysisResultRepository analysisResultRepository,
            CaseRepository caseRepository) {
        this.evidenceRepository = evidenceRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.caseRepository = caseRepository;
    }

    public Evidence uploadEvidence(String caseId, MultipartFile file) throws IOException {
        if (caseId == null || caseId.isBlank() || !caseRepository.existsById(caseId)) {
            throw new IllegalArgumentException("Select a valid investigation before uploading evidence.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a non-empty image file.");
        }

        String originalName = file.getOriginalFilename() == null ? "evidence" : file.getOriginalFilename();
        String normalizedName = originalName.replace('\\', '/');
        String safeName = Paths.get(normalizedName).getFileName().toString()
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

        Files.createDirectories(uploadDirectory);
        String storedName = UUID.randomUUID() + "_" + safeName;
        Path destination = uploadDirectory.resolve(storedName).normalize();
        if (!destination.startsWith(uploadDirectory)) {
            throw new IllegalArgumentException("Invalid file name.");
        }

        try (var input = file.getInputStream()) {
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            Files.deleteIfExists(destination);
            throw exception;
        }

        String contentType = jpeg ? "image/jpeg" : "image/png";
        Evidence evidence = new Evidence(
                caseId,
                safeName,
                contentType,
                file.getSize(),
                destination.toString(),
                LocalDateTime.now());

        return evidenceRepository.save(evidence);
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

        // Delete generated and uploaded files by their exact persisted paths.
        deleteIfPresent(evidence.getFilePath());
        deleteIfPresent(evidence.getElaPath());

        // Remove associated analysis documents so dashboards and reports stay consistent.
        analysisResultRepository.deleteByEvidenceId(evidenceId);
        evidenceRepository.deleteById(evidenceId);
    }

    private void deleteIfPresent(String storedPath) throws IOException {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }
        Path path = Paths.get(storedPath).toAbsolutePath().normalize();
        if (Files.isRegularFile(path)) {
            Files.deleteIfExists(path);
        }
    }
}
