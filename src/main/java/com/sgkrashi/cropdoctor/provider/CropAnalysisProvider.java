package com.sgkrashi.cropdoctor.provider;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * The anti-lock-in seam (Phase 1, Section 3.1 of the feature spec).
 * {@code CropDoctorService} depends only on this interface — swapping
 * providers (Gemini today, Plantix if/when API access is granted, a hybrid)
 * is a new implementation of this one method, not a rewrite of the service
 * layer, controller, or persistence.
 */
public interface CropAnalysisProvider {

    /**
     * @param images       1-3 validated image files
     * @param declaredCrop the user's declared crop (normalized), passed as
     *                     context so the provider can flag disagreement
     *                     explicitly rather than silently overriding it
     * @param language     requested report language (e.g. "en", "hi")
     */
    CropAnalysisResult analyze(List<MultipartFile> images, String declaredCrop, String language);
}
