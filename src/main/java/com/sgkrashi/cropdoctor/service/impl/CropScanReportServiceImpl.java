package com.sgkrashi.cropdoctor.service.impl;

import com.sgkrashi.cropdoctor.entity.CropScan;
import com.sgkrashi.cropdoctor.service.CropScanReportService;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Generates the one-page scan report PDF (Section 5.6) using OpenPDF — the
 * only PDF generation in this project, so nothing existing to reuse here.
 */
@Service
public class CropScanReportServiceImpl implements CropScanReportService {

    private static final Logger log = LoggerFactory.getLogger(CropScanReportServiceImpl.class);

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneOffset.UTC);

    private static final String DISCLAIMER =
            "AI Crop Doctor provides general guidance and is not a substitute for professional "
                    + "agricultural or plant pathology advice.";

    private final Path localUploadDir;

    public CropScanReportServiceImpl(@Value("${app.media.upload-dir:uploads}") String uploadDirPath) {
        this.localUploadDir = Path.of(uploadDirPath).toAbsolutePath().normalize();
    }

    @Override
    public byte[] generateReport(CropScan scan) {
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font uncertainFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.NORMAL, new Color(0xB4, 0x53, 0x09));
            Font mismatchFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.NORMAL, new Color(0xC6, 0x28, 0x28));
            Font mutedFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Font.ITALIC, Color.GRAY);

            document.add(new Paragraph("SG Krashi — AI Crop Doctor Report", titleFont));
            document.add(Chunk.NEWLINE);

            addImage(document, scan.getImageUrl(), mutedFont);
            document.add(Chunk.NEWLINE);

            if (scan.getDeclaredCrop() != null) {
                addField(document, headingFont, bodyFont, "Declared crop (by user)", scan.getDeclaredCrop());
            }
            addField(document, headingFont, bodyFont, "Crop (AI-detected)", scan.getCropName());
            addField(document, headingFont, bodyFont, "Result", scan.getDiseaseName());
            addField(document, headingFont, bodyFont, "Confidence", formatConfidence(scan.getConfidenceScore()));
            if (scan.getSeverity() != null) {
                addField(document, headingFont, bodyFont, "Severity", scan.getSeverity());
            }
            addField(document, headingFont, bodyFont, "Scan date", DATE_FORMAT.format(scan.getCreatedAt()) + " UTC");
            addField(document, headingFont, bodyFont, "Model version", scan.getModelVersion());
            document.add(Chunk.NEWLINE);

            // Independent banners — a scan can be mismatched, uncertain, both,
            // or neither; never conflated into one flag (see
            // CropDoctorServiceImpl).
            if (scan.isCropMismatch()) {
                document.add(new Paragraph(
                        "CROP MISMATCH — you selected \"" + scan.getDeclaredCrop() + "\", but the AI detected "
                                + "this photo as \"" + scan.getCropName() + "\". This result may not apply to "
                                + "your actual crop at all.",
                        mismatchFont));
                document.add(Chunk.NEWLINE);
            }

            if (scan.isUncertain()) {
                document.add(new Paragraph(
                        "UNCERTAIN RESULT — try a clearer photo or consult an agriculture expert.",
                        uncertainFont));
                document.add(Chunk.NEWLINE);
            }

            document.add(new Paragraph("Recommendation", headingFont));
            document.add(new Paragraph(scan.getRecommendation(), bodyFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph(DISCLAIMER, mutedFont));
        } catch (DocumentException ex) {
            throw new IllegalStateException("Failed to generate PDF report for scan " + scan.getId(), ex);
        } finally {
            document.close();
        }

        return out.toByteArray();
    }

    private void addImage(Document document, String imageUrl, Font mutedFont) throws DocumentException {
        try {
            byte[] imageBytes = loadImageBytes(imageUrl);
            Image image = Image.getInstance(imageBytes);
            image.scaleToFit(300, 300);
            image.setAlignment(Image.ALIGN_CENTER);
            document.add(image);
        } catch (Exception ex) {
            log.warn("Could not embed scan image in PDF report ({}): {}", imageUrl, ex.getMessage());
            document.add(new Paragraph("(Original image could not be loaded)", mutedFont));
        }
    }

    /**
     * {@code LocalStorageProvider} returns a relative {@code /uploads/...}
     * URL (served back out over HTTP only via WebConfig's resource handler,
     * not fetchable as a standalone absolute URL) while {@code
     * S3StorageProvider} returns a full {@code https://...} URL — this
     * service has no HTTP request context of its own to resolve the former
     * against, so it reads local files straight off disk instead.
     */
    private byte[] loadImageBytes(String imageUrl) throws IOException {
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            try (InputStream in = URI.create(imageUrl).toURL().openStream()) {
                return in.readAllBytes();
            }
        }
        String filename = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
        return Files.readAllBytes(localUploadDir.resolve(filename));
    }

    private void addField(Document document, Font headingFont, Font bodyFont, String label, String value)
            throws DocumentException {
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Chunk(label + ": ", headingFont));
        paragraph.add(new Chunk(value, bodyFont));
        document.add(paragraph);
    }

    private String formatConfidence(BigDecimal confidenceScore) {
        return confidenceScore.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP) + "%";
    }
}
