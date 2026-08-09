package com.sgkrashi.cropdoctor.service;

import com.sgkrashi.cropdoctor.dto.response.SupportedCropResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * The 14 crop names the model's 38 classes cover, derived directly from
 * sg-krashi-ai-service's {@code models/labels.json} (verified against the
 * real file, not assumed) — kept here as a small static lookup rather than
 * a call to the AI service, since it's effectively static configuration
 * tied to a specific model version (already tracked via {@code
 * CropScan.modelVersion}), not something worth a network round-trip or a
 * new failure mode for. Must be updated if the model/checkpoint ever changes.
 *
 * <p>A crop has limited coverage when it has exactly one class in the
 * model's output — either "healthy only" (Blueberry, Raspberry, Soybean —
 * the model can never detect a disease for these) or "disease only" (Orange,
 * Squash — the model can never report these as healthy). These are opposite
 * failure modes, so each gets its own precise note rather than one generic
 * "limited" flag — saying "healthy detection only" about Orange would itself
 * be dishonest, since Orange is exactly the reverse case.
 */
@Component
public class SupportedCropsProvider {

    private static final List<String> ALL_CROPS = List.of(
            "Apple", "Blueberry", "Cherry", "Corn", "Grape", "Orange", "Peach",
            "Pepper", "Potato", "Raspberry", "Soybean", "Squash", "Strawberry", "Tomato"
    );

    private static final Map<String, String> COVERAGE_NOTES = Map.of(
            "Blueberry", "AI can only detect Healthy for this crop — no disease classes in the model",
            "Orange", "AI can only detect a disease (Huanglongbing) for this crop — no healthy class in the model",
            "Raspberry", "AI can only detect Healthy for this crop — no disease classes in the model",
            "Soybean", "AI can only detect Healthy for this crop — no disease classes in the model",
            "Squash", "AI can only detect a disease (Powdery mildew) for this crop — no healthy class in the model"
    );

    public List<SupportedCropResponse> getSupportedCrops() {
        return ALL_CROPS.stream()
                .map(crop -> new SupportedCropResponse(crop, COVERAGE_NOTES.containsKey(crop), COVERAGE_NOTES.get(crop)))
                .toList();
    }

    public boolean isKnownCrop(String cropName) {
        return cropName != null && ALL_CROPS.contains(normalize(cropName));
    }

    /** Case-insensitive, whitespace-trimmed match — matches {@code CropDoctorServiceImpl}'s mismatch check. */
    public static String normalize(String cropName) {
        if (cropName == null) {
            return null;
        }
        String trimmed = cropName.trim();
        return ALL_CROPS.stream()
                .filter(crop -> crop.equalsIgnoreCase(trimmed))
                .findFirst()
                .orElse(trimmed);
    }
}
