package com.sgkrashi.cropdoctor.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgkrashi.cropdoctor.dto.response.CropScanResponse;
import com.sgkrashi.cropdoctor.dto.response.CropScanSummaryResponse;
import com.sgkrashi.cropdoctor.dto.response.GroundingSourceResponse;
import com.sgkrashi.cropdoctor.entity.CropScan;
import com.sgkrashi.cropdoctor.provider.ConfidenceBand;
import com.sgkrashi.cropdoctor.provider.CropAnalysisResult;
import com.sgkrashi.cropdoctor.provider.GroundingSource;
import com.sgkrashi.cropdoctor.provider.HealthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Normalizes both scan shapes that coexist in {@code crop_scans} — old
 * fixed-class-classifier rows (flat columns only) and new Gemini rows (full
 * {@link CropAnalysisResult} in {@code report_json}) — into one response
 * shape, so the frontend never needs to know which engine produced a given
 * scan.
 */
@Component
public class CropScanMapper {

    private static final Logger log = LoggerFactory.getLogger(CropScanMapper.class);
    private static final String LEGACY_PROVIDER_NAME = "local-mobilenetv2";
    private static final String LEGACY_LIMITATIONS =
            "This scan used the platform's original lightweight image classifier, which does not produce "
                    + "this level of report detail (no causes, environmental factors, or monitoring guidance).";

    private final ObjectMapper objectMapper;

    public CropScanMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CropScanResponse toResponse(CropScan scan) {
        CropAnalysisResult result = resolveResult(scan);
        return new CropScanResponse(
                scan.getId(),
                scan.getDeclaredCrop(),
                scan.getLanguage(),
                resolveImageUrls(scan),
                result.identifiedCrop(),
                result.healthStatus().name(),
                result.problem(),
                result.pathogenScientificName(),
                result.confidenceBand().name(),
                result.severity() == null ? null : result.severity().name(),
                result.symptoms(),
                result.possibleCauses(),
                result.environmentalFactors(),
                result.actionsNow(),
                result.prevention(),
                result.monitoringGuidance(),
                result.warningSignsToEscalate(),
                result.limitations(),
                result.providerName(),
                result.providerModelVersion(),
                scan.isUncertain(),
                scan.isCropMismatch(),
                mapGroundingSources(result.groundingSources()),
                scan.getCreatedAt()
        );
    }

    /**
     * A Guest's result — never persisted (Guest Access refinement), so
     * there's no {@code CropScan} entity to map from. Built directly from
     * the provider result so the frontend gets the exact same rich shape
     * either way; {@code id} is null, which the frontend uses to know
     * "there's nothing here to download or view again."
     */
    public CropScanResponse toEphemeralResponse(CropAnalysisResult result, String declaredCrop, String language) {
        return new CropScanResponse(
                null,
                declaredCrop,
                language,
                List.of(),
                result.identifiedCrop(),
                result.healthStatus().name(),
                result.problem(),
                result.pathogenScientificName(),
                result.confidenceBand().name(),
                result.severity() == null ? null : result.severity().name(),
                result.symptoms(),
                result.possibleCauses(),
                result.environmentalFactors(),
                result.actionsNow(),
                result.prevention(),
                result.monitoringGuidance(),
                result.warningSignsToEscalate(),
                result.limitations(),
                result.providerName(),
                result.providerModelVersion(),
                result.confidenceBand() == ConfidenceBand.LOW,
                !result.cropMatchesDeclared(),
                mapGroundingSources(result.groundingSources()),
                Instant.now()
        );
    }

    public CropScanSummaryResponse toSummaryResponse(CropScan scan) {
        CropAnalysisResult result = resolveResult(scan);
        return new CropScanSummaryResponse(
                scan.getId(),
                scan.getImageUrl(),
                result.identifiedCrop(),
                result.problem(),
                result.healthStatus().name(),
                result.confidenceBand().name(),
                scan.isUncertain(),
                scan.isCropMismatch(),
                scan.getCreatedAt()
        );
    }

    /** Also used directly by {@code CropScanReportServiceImpl} to build the PDF from the same normalized shape. */
    public CropAnalysisResult resolveResult(CropScan scan) {
        if (scan.getReportJson() != null) {
            try {
                return objectMapper.readValue(scan.getReportJson(), CropAnalysisResult.class);
            } catch (JsonProcessingException ex) {
                log.warn("Corrupt report_json for scan {}, falling back to legacy fields", scan.getId(), ex);
            }
        }
        return synthesizeFromLegacyFields(scan);
    }

    private List<GroundingSourceResponse> mapGroundingSources(List<GroundingSource> sources) {
        return sources.stream()
                .map(source -> new GroundingSourceResponse(source.title(), source.crop(), source.topic()))
                .toList();
    }

    public List<String> resolveImageUrls(CropScan scan) {
        if (scan.getImageUrls() != null) {
            try {
                return objectMapper.readValue(scan.getImageUrls(), new TypeReference<List<String>>() {
                });
            } catch (JsonProcessingException ex) {
                log.warn("Corrupt image_urls for scan {}, falling back to single image_url", scan.getId(), ex);
            }
        }
        return List.of(scan.getImageUrl());
    }

    /** Reconstructs a {@link CropAnalysisResult}-shaped view for scans predating Phase 1. */
    private CropAnalysisResult synthesizeFromLegacyFields(CropScan scan) {
        boolean isHealthy = scan.getDiseaseName() == null || "Healthy".equalsIgnoreCase(scan.getDiseaseName());
        return new CropAnalysisResult(
                scan.getCropName(),
                !scan.isCropMismatch(),
                isHealthy ? HealthStatus.HEALTHY : HealthStatus.DISEASED,
                scan.getDiseaseName(),
                null,
                deriveBand(scan.getConfidenceScore()),
                null,
                List.of(),
                List.of(),
                List.of(),
                scan.getRecommendation() == null ? List.of() : List.of(scan.getRecommendation()),
                List.of(),
                "Re-scan periodically to track how the plant develops.",
                List.of(),
                LEGACY_LIMITATIONS,
                LEGACY_PROVIDER_NAME,
                scan.getModelVersion(),
                List.of()
        );
    }

    private ConfidenceBand deriveBand(BigDecimal confidenceScore) {
        if (confidenceScore == null) {
            return ConfidenceBand.LOW;
        }
        if (confidenceScore.compareTo(new BigDecimal("0.85")) >= 0) {
            return ConfidenceBand.HIGH;
        }
        if (confidenceScore.compareTo(new BigDecimal("0.60")) >= 0) {
            return ConfidenceBand.MODERATE;
        }
        return ConfidenceBand.LOW;
    }
}
