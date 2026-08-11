package com.sgkrashi.cropdoctor.service.impl;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.List;
import com.lowagie.text.ListItem;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.sgkrashi.cropdoctor.entity.CropScan;
import com.sgkrashi.cropdoctor.mapper.CropScanMapper;
import com.sgkrashi.cropdoctor.provider.ConfidenceBand;
import com.sgkrashi.cropdoctor.provider.CropAnalysisResult;
import com.sgkrashi.cropdoctor.service.CropScanReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Generates the scan report PDF (Phase 1, Section 4.3) from the same
 * normalized {@link CropAnalysisResult} shape {@code CropScanMapper} builds
 * for the API/UI — the report never gets a stripped-down or stale version
 * of the honesty content (uncertainty, mismatch, limitations).
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
    private final CropScanMapper cropScanMapper;

    public CropScanReportServiceImpl(
            @Value("${app.media.upload-dir:uploads}") String uploadDirPath,
            CropScanMapper cropScanMapper
    ) {
        this.localUploadDir = Path.of(uploadDirPath).toAbsolutePath().normalize();
        this.cropScanMapper = cropScanMapper;
    }

    @Override
    public byte[] generateReport(CropScan scan) {
        CropAnalysisResult result = cropScanMapper.resolveResult(scan);
        java.util.List<String> imageUrls = cropScanMapper.resolveImageUrls(scan);

        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font fieldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font uncertainFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.NORMAL, new Color(0xB4, 0x53, 0x09));
            Font mismatchFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.NORMAL, new Color(0xC6, 0x28, 0x28));
            Font mutedFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Font.ITALIC, Color.GRAY);

            document.add(new Paragraph("SG Krashi — AI Crop Doctor Report", titleFont));
            document.add(Chunk.NEWLINE);

            addImages(document, imageUrls, mutedFont);
            document.add(Chunk.NEWLINE);

            if (scan.getDeclaredCrop() != null) {
                addField(document, fieldFont, bodyFont, "Declared crop (by user)", scan.getDeclaredCrop());
            }
            addField(document, fieldFont, bodyFont, "Crop (AI-detected)", result.identifiedCrop());
            addField(document, fieldFont, bodyFont, "Health status", capitalize(result.healthStatus().name()));
            if (result.problem() != null) {
                addField(document, fieldFont, bodyFont, "Problem", result.problem());
            }
            if (result.pathogenScientificName() != null) {
                addField(document, fieldFont, bodyFont, "Pathogen", result.pathogenScientificName());
            }
            addField(document, fieldFont, bodyFont, "Confidence", capitalize(result.confidenceBand().name()));
            if (result.severity() != null) {
                addField(document, fieldFont, bodyFont, "Severity", capitalize(result.severity().name()));
            }
            addField(document, fieldFont, bodyFont, "Scan date", DATE_FORMAT.format(scan.getCreatedAt()) + " UTC");
            addField(document, fieldFont, bodyFont, "Analysis provider", result.providerName() + " (" + result.providerModelVersion() + ")");
            document.add(Chunk.NEWLINE);

            // Independent banners — a scan can be mismatched, uncertain, both,
            // or neither; never conflated into one flag (see
            // CropDoctorServiceImpl).
            if (scan.isCropMismatch()) {
                document.add(new Paragraph(
                        "CROP MISMATCH — you selected \"" + scan.getDeclaredCrop() + "\", but the AI detected "
                                + "this photo as \"" + result.identifiedCrop() + "\". This result may not apply "
                                + "to your actual crop at all.",
                        mismatchFont));
                document.add(Chunk.NEWLINE);
            }

            if (result.confidenceBand() == ConfidenceBand.LOW) {
                document.add(new Paragraph(
                        "UNCERTAIN RESULT — try a clearer photo or consult an agriculture expert.",
                        uncertainFont));
                document.add(Chunk.NEWLINE);
            }

            addBulletSection(document, headingFont, bodyFont, "Symptoms", result.symptoms());
            addBulletSection(document, headingFont, bodyFont, "Possible Causes", result.possibleCauses());
            addBulletSection(document, headingFont, bodyFont, "Environmental Factors", result.environmentalFactors());
            addBulletSection(document, headingFont, bodyFont, "What To Do Now", result.actionsNow());
            addBulletSection(document, headingFont, bodyFont, "Prevention", result.prevention());

            if (result.monitoringGuidance() != null && !result.monitoringGuidance().isBlank()) {
                document.add(new Paragraph("Monitoring", headingFont));
                document.add(new Paragraph(result.monitoringGuidance(), bodyFont));
                document.add(Chunk.NEWLINE);
            }

            addBulletSection(document, headingFont, bodyFont, "Warning Signs — Escalate If You See", result.warningSignsToEscalate());

            document.add(new Paragraph("Limitations", headingFont));
            document.add(new Paragraph(result.limitations(), bodyFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph(DISCLAIMER, mutedFont));
        } catch (DocumentException ex) {
            throw new IllegalStateException("Failed to generate PDF report for scan " + scan.getId(), ex);
        } finally {
            document.close();
        }

        return out.toByteArray();
    }

    private void addBulletSection(Document document, Font headingFont, Font bodyFont, String heading, java.util.List<String> items)
            throws DocumentException {
        if (items == null || items.isEmpty()) {
            return;
        }
        document.add(new Paragraph(heading, headingFont));
        List bulletList = new List(List.UNORDERED, 12);
        for (String item : items) {
            bulletList.add(new ListItem(item, bodyFont));
        }
        document.add(bulletList);
        document.add(Chunk.NEWLINE);
    }

    private void addImages(Document document, java.util.List<String> imageUrls, Font mutedFont) throws DocumentException {
        // Multiple images are scaled down further so 2-3 still fit comfortably on one page.
        float maxSize = imageUrls.size() > 1 ? 180f : 300f;
        for (String imageUrl : imageUrls) {
            try {
                byte[] imageBytes = loadImageBytes(imageUrl);
                Image image = Image.getInstance(imageBytes);
                image.scaleToFit(maxSize, maxSize);
                image.setAlignment(Image.ALIGN_CENTER);
                document.add(image);
            } catch (Exception ex) {
                log.warn("Could not embed scan image in PDF report ({}): {}", imageUrl, ex.getMessage());
                document.add(new Paragraph("(Image could not be loaded)", mutedFont));
            }
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

    private void addField(Document document, Font fieldFont, Font bodyFont, String label, String value)
            throws DocumentException {
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Chunk(label + ": ", fieldFont));
        paragraph.add(new Chunk(value, bodyFont));
        document.add(paragraph);
    }

    private String capitalize(String enumName) {
        if (enumName == null || enumName.isEmpty()) {
            return enumName;
        }
        return enumName.charAt(0) + enumName.substring(1).toLowerCase(java.util.Locale.ROOT);
    }
}
