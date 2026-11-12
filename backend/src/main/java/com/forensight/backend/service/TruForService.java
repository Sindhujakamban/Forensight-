package com.forensight.backend.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Runs the user's existing TruFor model through the retained Python bridge. */
@Service
public class TruForService {

    private final boolean enabled;
    private final String python;
    private final String bridgePath;

    public TruForService(
            @Value("${forensight.trufor.enabled:false}") boolean enabled,
            @Value("${forensight.trufor.python:python3}") String python,
            @Value("${forensight.trufor.bridge:}") String bridgePath) {
        this.enabled = enabled;
        this.python = python;
        this.bridgePath = bridgePath;
    }

    public boolean isEnabled() { return enabled; }

    public Optional<TruForResult> analyzeIfConfigured(String imagePath, String evidenceId)
            throws IOException {
        if (!enabled) return Optional.empty();
        if (bridgePath == null || bridgePath.isBlank()) {
            throw new IOException("TruFor is enabled but the bridge path is empty.");
        }
        Path bridge = Paths.get(bridgePath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(bridge)) {
            throw new IOException("TruFor bridge not found: " + bridge);
        }
        if (imagePath == null || !Files.isRegularFile(Paths.get(imagePath))) {
            throw new IOException("Evidence image not found: " + imagePath);
        }

        List<String> command = new ArrayList<>();
        command.add(python);
        command.add(bridge.toString());
        command.add(imagePath);
        command.add(evidenceId == null ? "evidence" : evidenceId);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append('\n');
        }

        boolean finished;
        try {
            finished = process.waitFor(15, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("TruFor timed out after 15 minutes.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("TruFor process was interrupted.", ex);
        }

        String log = output.toString();
        if (process.exitValue() != 0) {
            throw new IOException("TruFor bridge failed (exit " + process.exitValue() + "):\n" + log);
        }

        double score = parseDouble(log, "Score:");
        double confidence = parseDouble(log, "Confidence:");
        String heatmap = parseText(log, "Heatmap:");
        String confidenceMap = parseText(log, "ConfidenceMap:");
        String npz = parseText(log, "NPZ:");

        validateRange(score, "TruFor score");
        validateRange(confidence, "TruFor confidence");
        requireFile(heatmap, "TruFor localization heatmap");
        requireFile(confidenceMap, "TruFor confidence map");
        requireFile(npz, "TruFor NPZ output");

        return Optional.of(new TruForResult(score, confidence, heatmap, confidenceMap, npz, log));
    }

    private double parseDouble(String text, String label) throws IOException {
        String value = parseText(text, label);
        try { return Double.parseDouble(value); }
        catch (NumberFormatException ex) { throw new IOException("Invalid " + label + " value: " + value, ex); }
    }

    private String parseText(String text, String label) throws IOException {
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(label)) return trimmed.substring(label.length()).trim();
        }
        throw new IOException("TruFor output is missing '" + label + "'. Full output:\n" + text);
    }

    private void validateRange(double value, String label) throws IOException {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IOException(label + " is outside the expected [0,1] range: " + value);
        }
    }

    private void requireFile(String value, String label) throws IOException {
        if (value.isBlank() || !Files.isRegularFile(Paths.get(value))) {
            throw new IOException(label + " was not created: " + value);
        }
    }

    public static class TruForResult {
        private final double score;
        private final double confidence;
        private final String heatmapPath;
        private final String confidenceMapPath;
        private final String npzPath;
        private final String rawOutput;

        public TruForResult(double score, double confidence, String heatmapPath,
                String confidenceMapPath, String npzPath, String rawOutput) {
            this.score = score;
            this.confidence = confidence;
            this.heatmapPath = heatmapPath;
            this.confidenceMapPath = confidenceMapPath;
            this.npzPath = npzPath;
            this.rawOutput = rawOutput;
        }
        public double getScore() { return score; }
        public double getConfidence() { return confidence; }
        public String getHeatmapPath() { return heatmapPath; }
        public String getConfidenceMapPath() { return confidenceMapPath; }
        public String getNpzPath() { return npzPath; }
        public String getRawOutput() { return rawOutput; }
    }
}
