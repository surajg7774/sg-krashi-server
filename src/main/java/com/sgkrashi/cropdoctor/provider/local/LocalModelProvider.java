package com.sgkrashi.cropdoctor.provider.local;

import com.sgkrashi.cropdoctor.dto.response.AiPredictionResponse;
import com.sgkrashi.cropdoctor.provider.ConfidenceBand;
import com.sgkrashi.cropdoctor.provider.CropAnalysisProvider;
import com.sgkrashi.cropdoctor.provider.CropAnalysisResult;
import com.sgkrashi.cropdoctor.provider.HealthStatus;
import com.sgkrashi.cropdoctor.service.AiServiceClient;
import com.sgkrashi.cropdoctor.service.RecommendationTextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

/**
 * Wraps the original PlantVillage/MobileNetV2 classifier (via {@code
 * AiServiceClient}, still calling sg-krashi-ai-service on Railway) behind
 * the new provider interface. Disabled by default (Section 3.7 of the
 * Phase 1 spec) — Gemini is the active V1 provider; this exists so the
 * original engine can be re-enabled with a single config flip
 * ({@code crop-doctor.provider=local}) if ever needed, at zero code changes.
 *
 * <p>The wrapped classifier only ever supported one image and a flat
 * crop/disease/confidence shape — mapped onto the richer {@link
 * CropAnalysisResult} as honestly as possible below, not padded out with
 * invented detail the old model never produced.
 */
@Component
@ConditionalOnProperty(prefix = "crop-doctor", name = "provider", havingValue = "local")
public class LocalModelProvider implements CropAnalysisProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalModelProvider.class);
    private static final String PROVIDER_NAME = "local-mobilenetv2";
    private static final String HEALTHY_LABEL = "Healthy";

    private final AiServiceClient aiServiceClient;
    private final RecommendationTextProvider recommendationTextProvider;

    public LocalModelProvider(AiServiceClient aiServiceClient, RecommendationTextProvider recommendationTextProvider) {
        this.aiServiceClient = aiServiceClient;
        this.recommendationTextProvider = recommendationTextProvider;
    }

    @Override
    public CropAnalysisResult analyze(List<MultipartFile> images, String declaredCrop, String language) {
        if (images.size() > 1) {
            log.info("Local model only supports a single image — using the first of {} submitted", images.size());
        }
        MultipartFile firstImage = images.get(0);

        AiPredictionResponse prediction = aiServiceClient.predict(firstImage);

        boolean isHealthy = HEALTHY_LABEL.equals(prediction.diseaseName());
        String recommendation = recommendationTextProvider.recommendationFor(prediction.classLabel(), isHealthy);

        return new CropAnalysisResult(
                prediction.cropName(),
                declaredCrop.equalsIgnoreCase(prediction.cropName()),
                isHealthy ? HealthStatus.HEALTHY : HealthStatus.DISEASED,
                isHealthy ? null : prediction.diseaseName(),
                null,
                confidenceBandFor(prediction.confidenceScore()),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(recommendation),
                List.of(),
                "Re-scan periodically to track how the plant develops.",
                List.of(),
                "This result comes from a lightweight image classifier limited to 14 crops and 38 fixed "
                        + "categories, with no ability to explain causes or generate a full report — it is the "
                        + "platform's original, lower-detail fallback engine, not the primary one.",
                PROVIDER_NAME,
                prediction.modelVersion()
        );
    }

    private ConfidenceBand confidenceBandFor(BigDecimal confidenceScore) {
        if (confidenceScore.compareTo(new BigDecimal("0.85")) >= 0) {
            return ConfidenceBand.HIGH;
        }
        if (confidenceScore.compareTo(new BigDecimal("0.60")) >= 0) {
            return ConfidenceBand.MODERATE;
        }
        return ConfidenceBand.LOW;
    }
}
