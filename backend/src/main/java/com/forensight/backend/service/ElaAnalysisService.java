package com.forensight.backend.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import java.io.File;
import java.io.IOException;

import java.util.Iterator;

@Service
public class ElaAnalysisService {

    // =========================================================
    // ELA SETTINGS
    // =========================================================

    /*
     * JPEG quality used to create the reference image.
     *
     * ELA compares the original JPEG against this
     * recompressed version.
     */
    private static final float JPEG_QUALITY = 0.75f;

    /*
     * Average RGB difference above this value is considered
     * suspicious by the initial ELA screening.
     *
     * IMPORTANT:
     * This is a heuristic threshold.
     * It is NOT a scientifically calibrated probability.
     */
    private static final double SUSPICIOUS_THRESHOLD = 8.0;


    // =========================================================
    // MAIN ANALYSIS
    // =========================================================

    public ElaResult analyze(String filePath) throws IOException {

        File originalFile = new File(filePath);

        // -----------------------------------------------------
        // Check file
        // -----------------------------------------------------

        if (!originalFile.exists()) {

            throw new IOException(
                    "Image file not found: " + filePath
            );
        }


        BufferedImage original =
                ImageIO.read(originalFile);


        if (original == null) {

            throw new IOException(
                    "Unable to read image"
            );
        }
        // -----------------------------------------------------
        // Recompress supported image inputs as JPEG
        // -----------------------------------------------------
        // This preserves the original ForenSight behavior for JPEG and
        // PNG files. For PNG, interpret differences as a recompression
        // screening signal rather than proof of the original edit history.

// -----------------------------------------------------
        // Create temporary recompressed JPEG
        // -----------------------------------------------------

        File tempFile =
                File.createTempFile(
                        "ela_",
                        ".jpg"
                );


        try {

            // -------------------------------------------------
            // Recompress original image
            // -------------------------------------------------

            saveAsJpeg(
                    original,
                    tempFile,
                    JPEG_QUALITY
            );


            BufferedImage recompressed =
                    ImageIO.read(tempFile);


            if (recompressed == null) {

                throw new IOException(
                        "Unable to read recompressed image"
                );
            }


            // -------------------------------------------------
            // Calculate average pixel difference
            // -------------------------------------------------

            double averageDifference =
                    calculateAverageDifference(
                            original,
                            recompressed
                    );


            // -------------------------------------------------
            // Determine result
            // -------------------------------------------------

            String result;
            String explanation;
            double confidence;


            if (averageDifference > SUSPICIOUS_THRESHOLD) {

                // ---------------------------------------------
                // SUSPICIOUS
                // ---------------------------------------------

                result = "SUSPICIOUS";

                /*
                 * Confidence increases as the difference
                 * becomes larger.
                 */
                confidence =
                        Math.min(
                                averageDifference
                                        / SUSPICIOUS_THRESHOLD,
                                1.0
                        );


                explanation =
                        "Significant JPEG compression differences "
                        + "were detected during the initial ELA "
                        + "screening. These differences may indicate "
                        + "possible image manipulation or regions "
                        + "with different compression histories. "
                        + "Further forensic examination is recommended.";

            } else {

                // ---------------------------------------------
                // LIKELY AUTHENTIC
                // ---------------------------------------------

                result = "LIKELY_AUTHENTIC";

                /*
                 * Lower ELA difference means higher confidence
                 * for this initial screening.
                 */
                confidence =
                        Math.max(
                                0.0,
                                1.0 -
                                        (
                                                averageDifference
                                                        / SUSPICIOUS_THRESHOLD
                                        )
                        );


                explanation =
                        "No significant JPEG compression "
                        + "differences were detected during the "
                        + "initial ELA screening. The image appears "
                        + "consistent with a similar JPEG compression "
                        + "history, although ELA alone cannot "
                        + "guarantee authenticity.";
            }


            // -------------------------------------------------
            // Return result
            // -------------------------------------------------

            return new ElaResult(
                    result,
                    confidence,
                    explanation
            );


        } finally {

            // -------------------------------------------------
            // Delete temporary file
            // -------------------------------------------------

            if (tempFile.exists()) {

                if (!tempFile.delete()) {

                    tempFile.deleteOnExit();
                }
            }
        }
    }


    // =========================================================
    // SAVE IMAGE AS JPEG
    // =========================================================

    private void saveAsJpeg(
            BufferedImage image,
            File output,
            float quality
    ) throws IOException {


        Iterator<ImageWriter> writers =
                ImageIO.getImageWritersByFormatName("jpg");


        if (!writers.hasNext()) {

            throw new IOException(
                    "JPEG writer not available"
            );
        }


        ImageWriter writer =
                writers.next();


        try (
                ImageOutputStream outputStream =
                        ImageIO.createImageOutputStream(output)
        ) {

            writer.setOutput(outputStream);


            ImageWriteParam params =
                    writer.getDefaultWriteParam();


            params.setCompressionMode(
                    ImageWriteParam.MODE_EXPLICIT
            );


            params.setCompressionQuality(
                    quality
            );


            // -------------------------------------------------
            // Convert image to RGB
            // -------------------------------------------------

            BufferedImage rgbImage =
                    new BufferedImage(
                            image.getWidth(),
                            image.getHeight(),
                            BufferedImage.TYPE_INT_RGB
                    );


            Graphics2D graphics =
                    rgbImage.createGraphics();


            graphics.drawImage(
                    image,
                    0,
                    0,
                    null
            );


            graphics.dispose();


            // -------------------------------------------------
            // Write JPEG
            // -------------------------------------------------

            writer.write(
                    null,
                    new javax.imageio.IIOImage(
                            rgbImage,
                            null,
                            null
                    ),
                    params
            );

        } finally {

            writer.dispose();
        }
    }


    // =========================================================
    // CALCULATE AVERAGE ELA DIFFERENCE
    // =========================================================

    private double calculateAverageDifference(
            BufferedImage original,
            BufferedImage recompressed
    ) {

        int width =
                Math.min(
                        original.getWidth(),
                        recompressed.getWidth()
                );


        int height =
                Math.min(
                        original.getHeight(),
                        recompressed.getHeight()
                );


        long pixelCount =
                (long) width * height;


        if (pixelCount == 0) {

            return 0.0;
        }


        long totalDifference = 0;


        // -----------------------------------------------------
        // Compare every pixel
        // -----------------------------------------------------

        for (int y = 0; y < height; y++) {

            for (int x = 0; x < width; x++) {

                int originalRgb =
                        original.getRGB(x, y);


                int recompressedRgb =
                        recompressed.getRGB(x, y);


                // ---------------------------------------------
                // Original RGB
                // ---------------------------------------------

                int r1 =
                        (originalRgb >> 16) & 0xFF;

                int g1 =
                        (originalRgb >> 8) & 0xFF;

                int b1 =
                        originalRgb & 0xFF;


                // ---------------------------------------------
                // Recompressed RGB
                // ---------------------------------------------

                int r2 =
                        (recompressedRgb >> 16) & 0xFF;

                int g2 =
                        (recompressedRgb >> 8) & 0xFF;

                int b2 =
                        recompressedRgb & 0xFF;


                // ---------------------------------------------
                // Difference
                // ---------------------------------------------

                totalDifference +=
                        Math.abs(r1 - r2);

                totalDifference +=
                        Math.abs(g1 - g2);

                totalDifference +=
                        Math.abs(b1 - b2);
            }
        }


        return (double) totalDifference /
                (pixelCount * 3);
    }


    // =========================================================
    // GENERATE ELA IMAGE
    // =========================================================

    public String generateElaImage(
            String filePath,
            String evidenceId
    ) throws IOException {


        File originalFile =
                new File(filePath);


        if (!originalFile.exists()) {

            throw new IOException(
                    "Original image not found: "
                            + filePath
            );
        }


        BufferedImage original =
                ImageIO.read(originalFile);


        if (original == null) {

            throw new IOException(
                    "Unable to read image"
            );
        }
        // JPEG and PNG source images are both accepted. The ELA visualization
        // is generated by recompressing the source to JPEG for comparison.

// -----------------------------------------------------
        // Recompress original
        // -----------------------------------------------------

        File tempFile =
                File.createTempFile(
                        "ela_image_",
                        ".jpg"
                );


        try {

            saveAsJpeg(
                    original,
                    tempFile,
                    JPEG_QUALITY
            );


            BufferedImage recompressed =
                    ImageIO.read(tempFile);


            if (recompressed == null) {

                throw new IOException(
                        "Unable to read recompressed image"
                );
            }


            // -------------------------------------------------
            // Create output folder
            // -------------------------------------------------

            File folder =
                    new File(
                            "uploads/ela"
                    );


            if (!folder.exists()) {

                if (!folder.mkdirs()
                        && !folder.exists()) {

                    throw new IOException(
                            "Unable to create ELA directory"
                    );
                }
            }


            // -------------------------------------------------
            // Output file
            // -------------------------------------------------

            File elaFile =
                    new File(
                            folder,
                            evidenceId + "_ela.jpg"
                    );


            // -------------------------------------------------
            // Create ELA difference image
            // -------------------------------------------------

            BufferedImage elaImage =
                    new BufferedImage(
                            original.getWidth(),
                            original.getHeight(),
                            BufferedImage.TYPE_INT_RGB
                    );


            for (int y = 0;
                 y < original.getHeight();
                 y++) {

                for (int x = 0;
                     x < original.getWidth();
                     x++) {


                    int originalRgb =
                            original.getRGB(x, y);


                    int recompressedRgb =
                            recompressed.getRGB(x, y);


                    // -----------------------------------------
                    // Original channels
                    // -----------------------------------------

                    int r1 =
                            (originalRgb >> 16) & 0xFF;

                    int g1 =
                            (originalRgb >> 8) & 0xFF;

                    int b1 =
                            originalRgb & 0xFF;


                    // -----------------------------------------
                    // Recompressed channels
                    // -----------------------------------------

                    int r2 =
                            (recompressedRgb >> 16) & 0xFF;

                    int g2 =
                            (recompressedRgb >> 8) & 0xFF;

                    int b2 =
                            recompressedRgb & 0xFF;


                    // -----------------------------------------
                    // Absolute difference
                    // -----------------------------------------

                    int diffR =
                            Math.abs(r1 - r2);

                    int diffG =
                            Math.abs(g1 - g2);

                    int diffB =
                            Math.abs(b1 - b2);


                    /*
                     * Amplify small differences so they become
                     * visible in the ELA image.
                     */
                    int amplifiedR =
                            Math.min(
                                    255,
                                    diffR * 10
                            );

                    int amplifiedG =
                            Math.min(
                                    255,
                                    diffG * 10
                            );

                    int amplifiedB =
                            Math.min(
                                    255,
                                    diffB * 10
                            );


                    int newRgb =
                            (amplifiedR << 16)
                                    | (amplifiedG << 8)
                                    | amplifiedB;


                    elaImage.setRGB(
                            x,
                            y,
                            newRgb
                    );
                }
            }


            // -------------------------------------------------
            // Save ELA image
            // -------------------------------------------------

            ImageIO.write(
                    elaImage,
                    "jpg",
                    elaFile
            );


            return elaFile.getPath();


        } finally {

            // -------------------------------------------------
            // Delete temporary file
            // -------------------------------------------------

            if (tempFile.exists()) {

                if (!tempFile.delete()) {

                    tempFile.deleteOnExit();
                }
            }
        }
    }


    // =========================================================
    // GET FILE EXTENSION
    // =========================================================

    private String getExtension(File file) {

        String name =
                file.getName();


        int dot =
                name.lastIndexOf('.');


        if (dot == -1) {

            return "";
        }


        return name.substring(
                dot + 1
        );
    }


    // =========================================================
    // ELA RESULT
    // =========================================================

    public static class ElaResult {

        private final String result;

        private final double confidence;

        private final String explanation;


        public ElaResult(
                String result,
                double confidence,
                String explanation
        ) {

            this.result =
                    result;

            this.confidence =
                    confidence;

            this.explanation =
                    explanation;
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