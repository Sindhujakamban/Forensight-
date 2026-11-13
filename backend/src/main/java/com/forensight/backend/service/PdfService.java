package com.forensight.backend.service;

import com.forensight.backend.model.ForensicReport;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


@Service
public class PdfService {


    // =========================================================
    // GENERATE FORENSIC PDF
    // =========================================================

    public byte[] generateReportPdf(ForensicReport report) {

        try {

            ByteArrayOutputStream outputStream =
                    new ByteArrayOutputStream();


            Document document =
                    new Document(
                            com.lowagie.text.PageSize.A4,
                            40,
                            40,
                            40,
                            40
                    );


            PdfWriter.getInstance(
                    document,
                    outputStream
            );


            document.open();


            // =================================================
            // FONTS
            // =================================================

            Font titleFont =
                    new Font(
                            Font.HELVETICA,
                            22,
                            Font.BOLD
                    );


            Font subtitleFont =
                    new Font(
                            Font.HELVETICA,
                            12,
                            Font.NORMAL
                    );


            Font sectionFont =
                    new Font(
                            Font.HELVETICA,
                            15,
                            Font.BOLD
                    );


            Font labelFont =
                    new Font(
                            Font.HELVETICA,
                            10,
                            Font.BOLD
                    );


            Font normalFont =
                    new Font(
                            Font.HELVETICA,
                            10,
                            Font.NORMAL
                    );


            Font resultFont =
                    new Font(
                            Font.HELVETICA,
                            16,
                            Font.BOLD
                    );


            // =================================================
            // HEADER
            // =================================================

            Paragraph title =
                    new Paragraph(
                            "ForenSight AI",
                            titleFont
                    );

            title.setAlignment(Element.ALIGN_CENTER);

            document.add(title);


            Paragraph subtitle =
                    new Paragraph(
                            "Digital Image Forensics & Investigation Platform",
                            subtitleFont
                    );

            subtitle.setAlignment(Element.ALIGN_CENTER);

            document.add(subtitle);


            document.add(
                    new Paragraph("\n")
            );


            // =================================================
            // REPORT TITLE
            // =================================================

            Paragraph reportTitle =
                    new Paragraph(
                            "FORENSIC ANALYSIS REPORT",
                            sectionFont
                    );

            reportTitle.setAlignment(
                    Element.ALIGN_CENTER
            );

            document.add(reportTitle);


            document.add(
                    new Paragraph("\n")
            );


            // =================================================
            // REPORT INFORMATION
            // =================================================

            PdfPTable informationTable =
                    new PdfPTable(2);

            informationTable.setWidthPercentage(100);

            informationTable.setWidths(
                    new float[]{35, 65}
            );


            addInformationRow(
                    informationTable,
                    "Evidence ID",
                    report.getEvidenceId(),
                    labelFont,
                    normalFont
            );


            addInformationRow(
                    informationTable,
                    "Case ID",
                    report.getCaseId(),
                    labelFont,
                    normalFont
            );


            addInformationRow(
                    informationTable,
                    "File Name",
                    report.getFileName(),
                    labelFont,
                    normalFont
            );


            addInformationRow(
                    informationTable,
                    "File Type",
                    report.getFileType(),
                    labelFont,
                    normalFont
            );


            addInformationRow(
                    informationTable,
                    "File Size",
                    formatFileSize(report.getFileSize()),
                    labelFont,
                    normalFont
            );


            String generatedAt =
                    LocalDateTime.now()
                            .format(
                                    DateTimeFormatter.ofPattern(
                                            "dd-MM-yyyy HH:mm:ss"
                                    )
                            );


            addInformationRow(
                    informationTable,
                    "Report Generated",
                    generatedAt,
                    labelFont,
                    normalFont
            );


            document.add(
                    informationTable
            );

            document.add(new Paragraph("\n"));

            // Analysis summary and method disclosure.
            Paragraph summaryHeading = new Paragraph("Screening Summary", sectionFont);
            document.add(summaryHeading);
            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(100);
            summaryTable.setWidths(new float[]{35, 65});
            addInformationRow(summaryTable, "Final Result", report.getResult(), labelFont, normalFont);
            addInformationRow(summaryTable, "Risk Level", report.getRiskLevel(), labelFont, normalFont);
            addInformationRow(summaryTable, "Final Authenticity Score",
                    report.getFinalScore() == null ? "Not available"
                            : String.format("%.2f%%", report.getFinalScore()),
                    labelFont, normalFont);
            addInformationRow(summaryTable, "ELA Supporting Score",
                    report.getElaScore() == null ? "Not available"
                            : String.format("%.2f%%", report.getElaScore()),
                    labelFont, normalFont);
            addInformationRow(summaryTable, "TruFor Authenticity",
                    report.getTruForAuthenticity() == null ? "Not available"
                            : String.format("%.2f%%", report.getTruForAuthenticity()),
                    labelFont, normalFont);
            addInformationRow(summaryTable, "TruFor Confidence",
                    report.getTruForConfidence() == null ? "Not available"
                            : String.format("%.2f%%", report.getTruForConfidence() * 100.0),
                    labelFont, normalFont);
            addInformationRow(summaryTable, "Scoring Formula",
                    report.isElaSupported() ? "TruFor 75% + ELA 25%" : "TruFor only (ELA unavailable)",
                    labelFont, normalFont);
            addInformationRow(summaryTable, "Metadata",
                    "Context only; not included in the authenticity score", labelFont, normalFont);
            addInformationRow(summaryTable, "TruFor Status", report.getTruForStatus(), labelFont, normalFont);
            if (report.isTruForAvailable()) {
                addInformationRow(summaryTable, "TruFor Attribution",
                        "GRIP-UNINA, University of Naples Federico II, CVPR 2023. Informational and nonprofit use only.",
                        labelFont, normalFont);
            }
            document.add(summaryTable);

            document.add(new Paragraph("\n"));

            // Include extracted metadata in the generated PDF report.
            Paragraph metadataHeading = new Paragraph("Extracted Image Metadata", sectionFont);
            document.add(metadataHeading);
            PdfPTable metadataTable = new PdfPTable(2);
            metadataTable.setWidthPercentage(100);
            metadataTable.setWidths(new float[]{35, 65});
            if (report.getMetadata() == null || report.getMetadata().isEmpty()) {
                addInformationRow(metadataTable, "Metadata", "No readable metadata was extracted.",
                        labelFont, normalFont);
            } else {
                report.getMetadata().forEach((key, value) ->
                        addInformationRow(metadataTable, key, value, labelFont, normalFont));
            }
            document.add(metadataTable);

            document.add(new Paragraph("\n"));


            // =================================================
            // ANALYSIS RESULT
            // =================================================

            Paragraph analysisHeading =
                    new Paragraph(
                            "Analysis Result",
                            sectionFont
                    );

            document.add(analysisHeading);


            document.add(
                    new Paragraph("\n")
            );


            Paragraph result =
                    new Paragraph(
                            report.getResult(),
                            resultFont
                    );

            result.setAlignment(
                    Element.ALIGN_CENTER
            );

            document.add(result);


            document.add(
                    new Paragraph("\n")
            );


            String confidenceText = report.getFinalScore() == null
                    ? "Final authenticity score: not available."
                    : String.format("Final authenticity score: %.2f%%", report.getFinalScore());

            Paragraph confidenceParagraph =
                    new Paragraph(
                            confidenceText,
                            normalFont
                    );

            confidenceParagraph.setAlignment(
                    Element.ALIGN_CENTER
            );

            document.add(
                    confidenceParagraph
            );


            document.add(
                    new Paragraph("\n")
            );


            // =================================================
            // ORIGINAL IMAGE
            // =================================================

            Paragraph originalHeading =
                    new Paragraph(
                            "Original Evidence Image",
                            sectionFont
                    );

            document.add(originalHeading);


            document.add(
                    new Paragraph("\n")
            );


            addImageToDocument(
                    document,
                    report.getOriginalImage()
            );


            document.add(
                    new Paragraph("\n")
            );


            // =================================================
            // ELA IMAGE
            // =================================================

            Paragraph elaHeading =
                    new Paragraph(
                            "Error Level Analysis (ELA)",
                            sectionFont
                    );

            document.add(elaHeading);


            document.add(
                    new Paragraph("\n")
            );


            addImageToDocument(
                    document,
                    report.getElaImage()
            );


            document.add(
                    new Paragraph("\n")
            );

            // =================================================
            // ORIGINAL TRUFOR LOCALIZATION HEATMAP
            // =================================================
            Paragraph truforHeading = new Paragraph("TruFor Forensic Heatmap", sectionFont);
            document.add(truforHeading);
            document.add(new Paragraph("\n"));
            if (report.isTruForAvailable()) {
                addImageToDocument(document, report.getTruforImage());
                Paragraph truforCaption = new Paragraph(
                        "The TruFor heatmap visualizes the spatial forensic signal produced by the AI model. "
                        + "Brighter regions indicate stronger model response and should be interpreted as supporting evidence, "
                        + "not standalone proof of manipulation.", normalFont);
                truforCaption.setAlignment(Element.ALIGN_JUSTIFIED);
                document.add(truforCaption);
                document.add(new Paragraph("\n"));

                Paragraph confidenceMapHeading = new Paragraph("TruFor Confidence Map", sectionFont);
                document.add(confidenceMapHeading);
                document.add(new Paragraph("\n"));
                addImageToDocument(document, report.getTruforConfidenceImage());
            } else {
                document.add(new Paragraph("TruFor heatmap not available for this analysis.", normalFont));
            }
            document.add(new Paragraph("\n"));

            // =================================================
            // FORENSIC EXPLANATION
            // =================================================

            Paragraph explanationHeading =
                    new Paragraph(
                            "Forensic Explanation",
                            sectionFont
                    );

            document.add(
                    explanationHeading
            );


            document.add(
                    new Paragraph("\n")
            );


            Paragraph explanation =
                    new Paragraph(
                            report.getExplanation(),
                            normalFont
                    );

            explanation.setAlignment(
                    Element.ALIGN_JUSTIFIED
            );

            document.add(
                    explanation
            );


            document.add(
                    new Paragraph("\n\n")
            );


            // =================================================
            // FOOTER
            // =================================================

            Paragraph footer =
                    new Paragraph(
                            "ForenSight AI - Digital Image Forensics",
                            subtitleFont
                    );

            footer.setAlignment(
                    Element.ALIGN_CENTER
            );

            document.add(footer);


            Paragraph disclaimer =
                    new Paragraph(
                            "This report is generated from automated forensic screening "
                            + "and should be interpreted as an analytical aid.",
                            new Font(
                                    Font.HELVETICA,
                                    8,
                                    Font.ITALIC
                            )
                    );

            disclaimer.setAlignment(
                    Element.ALIGN_CENTER
            );

            document.add(disclaimer);


            // =================================================
            // CLOSE PDF
            // =================================================

            document.close();


            return outputStream.toByteArray();


        } catch (Exception e) {

            e.printStackTrace();

            throw new RuntimeException(
                    "PDF generation failed: "
                            + e.getMessage()
            );

        }

    }


    // =========================================================
    // ADD INFORMATION ROW
    // =========================================================

    private void addInformationRow(
            PdfPTable table,
            String label,
            String value,
            Font labelFont,
            Font valueFont
    ) {


        PdfPCell labelCell =
                new PdfPCell(
                        new Phrase(
                                label,
                                labelFont
                        )
                );


        PdfPCell valueCell =
                new PdfPCell(
                        new Phrase(
                                value == null
                                        ? "N/A"
                                        : value,
                                valueFont
                        )
                );


        labelCell.setPadding(8);

        valueCell.setPadding(8);


        table.addCell(labelCell);

        table.addCell(valueCell);

    }


    // =========================================================
    // ADD IMAGE
    // =========================================================

    private void addImageToDocument(
            Document document,
            String imagePath
    ) {


        if (imagePath == null ||
                imagePath.isBlank()) {

            document.add(
                    new Paragraph(
                            "Image not available."
                    )
            );

            return;

        }


        try {


            // Convert Windows path to normal path

            String cleanPath =
                    imagePath
                            .replace("\\", File.separator)
                            .replace("/", File.separator);


            File imageFile =
                    new File(cleanPath);


            if (!imageFile.exists()) {

                // Try relative path from project directory

                imageFile =
                        new File(
                                System.getProperty(
                                        "user.dir"
                                ),
                                cleanPath
                        );

            }


            if (!imageFile.exists()) {

                document.add(
                        new Paragraph(
                                "Image file not found: "
                                        + imagePath
                        )
                );

                return;

            }


            Image image =
                    Image.getInstance(
                            imageFile.getAbsolutePath()
                    );


            // Fit image inside PDF page

            image.scaleToFit(
                    480,
                    320
            );


            image.setAlignment(
                    Element.ALIGN_CENTER
            );


            document.add(image);


        } catch (Exception e) {


            document.add(
                    new Paragraph(
                            "Unable to load image: "
                                    + imagePath
                    )
            );

        }

    }


    // =========================================================
    // FILE SIZE FORMAT
    // =========================================================

    private String formatFileSize(
            long bytes
    ) {


        if (bytes < 1024) {

            return bytes + " Bytes";

        }


        if (bytes < 1024 * 1024) {

            return String.format(
                    "%.2f KB",
                    bytes / 1024.0
            );

        }


        return String.format(
                "%.2f MB",
                bytes / (1024.0 * 1024.0)
        );

    }

}