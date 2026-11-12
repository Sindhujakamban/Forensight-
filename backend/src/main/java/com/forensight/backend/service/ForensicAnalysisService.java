package com.forensight.backend.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

@Service
public class ForensicAnalysisService {

    public AnalysisData analyzeImage(String filePath) throws IOException {

        File imageFile = new File(filePath);

        if (!imageFile.exists()) {
            throw new IOException("Image file not found: " + filePath);
        }

        BufferedImage image = ImageIO.read(imageFile);

        if (image == null) {
            throw new IOException("File is not a valid image");
        }

        int width = image.getWidth();
        int height = image.getHeight();

        long fileSize = imageFile.length();

        /*
         * Basic forensic checks.
         * This is the first version.
         * Advanced AI analysis will be added later.
         */

        String result = "CLEAN";
        double confidence = 0.70;

        String explanation =
                "Basic image validation completed. "
                + "Image dimensions: " + width + "x" + height
                + ", file size: " + fileSize + " bytes.";

        return new AnalysisData(
                result,
                confidence,
                explanation
        );
    }

    public static class AnalysisData {

        private final String result;
        private final double confidence;
        private final String explanation;

        public AnalysisData(
                String result,
                double confidence,
                String explanation) {

            this.result = result;
            this.confidence = confidence;
            this.explanation = explanation;
        }

        public String getResult() {
            return result;
        }

        public double getConfidence() {
            return confidence;
        }

        public String getExplanation() {
            return explanation;
        }
    }
}