package com.sgkrashi.cropdoctor.service.impl;

import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.RateLimitExceededException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.common.exception.ValidationException;
import com.sgkrashi.cropdoctor.dto.response.AiPredictionResponse;
import com.sgkrashi.cropdoctor.dto.response.CropScanReport;
import com.sgkrashi.cropdoctor.dto.response.CropScanResponse;
import com.sgkrashi.cropdoctor.dto.response.CropScanSummaryResponse;
import com.sgkrashi.cropdoctor.dto.response.SupportedCropResponse;
import com.sgkrashi.cropdoctor.entity.CropScan;
import com.sgkrashi.cropdoctor.mapper.CropScanMapper;
import com.sgkrashi.cropdoctor.ratelimit.AiCropDoctorRateLimiter;
import com.sgkrashi.cropdoctor.repository.CropScanRepository;
import com.sgkrashi.cropdoctor.service.AiServiceClient;
import com.sgkrashi.cropdoctor.service.CropDoctorService;
import com.sgkrashi.cropdoctor.service.CropScanReportService;
import com.sgkrashi.cropdoctor.service.RecommendationTextProvider;
import com.sgkrashi.cropdoctor.service.SupportedCropsProvider;
import com.sgkrashi.media.storage.StorageProvider;
import com.sgkrashi.media.validation.ImageContentTypeDetector;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CropDoctorServiceImpl implements CropDoctorService {

    private static final long MAX_FILE_SIZE_BYTES = 8L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final int MIN_DIMENSION_PX = 32;
    // Below this, the platform tells the user the result is uncertain rather
    // than presenting it with the same confidence as a strong match — see
    // Section 8 of the feature spec. 60% is a starting point, not a
    // scientifically derived cutoff; see sg-krashi-ai-service/README.md for
    // real accuracy numbers this was chosen against.
    private static final BigDecimal UNCERTAINTY_THRESHOLD = new BigDecimal("0.60");
    private static final String HEALTHY_LABEL = "Healthy";

    private final StorageProvider storageProvider;
    private final AiServiceClient aiServiceClient;
    private final CropScanRepository cropScanRepository;
    private final CropScanMapper cropScanMapper;
    private final RecommendationTextProvider recommendationTextProvider;
    private final CropScanReportService cropScanReportService;
    private final CurrentUserProvider currentUserProvider;
    private final AiCropDoctorRateLimiter rateLimiter;
    private final SupportedCropsProvider supportedCropsProvider;

    public CropDoctorServiceImpl(
            StorageProvider storageProvider,
            AiServiceClient aiServiceClient,
            CropScanRepository cropScanRepository,
            CropScanMapper cropScanMapper,
            RecommendationTextProvider recommendationTextProvider,
            CropScanReportService cropScanReportService,
            CurrentUserProvider currentUserProvider,
            AiCropDoctorRateLimiter rateLimiter,
            SupportedCropsProvider supportedCropsProvider
    ) {
        this.storageProvider = storageProvider;
        this.aiServiceClient = aiServiceClient;
        this.cropScanRepository = cropScanRepository;
        this.cropScanMapper = cropScanMapper;
        this.recommendationTextProvider = recommendationTextProvider;
        this.cropScanReportService = cropScanReportService;
        this.currentUserProvider = currentUserProvider;
        this.rateLimiter = rateLimiter;
        this.supportedCropsProvider = supportedCropsProvider;
    }

    @Override
    @Transactional
    public CropScanResponse analyze(MultipartFile file, String declaredCrop) {
        Long userId = currentUserProvider.getCurrentUserId();

        if (!rateLimiter.tryConsume(String.valueOf(userId))) {
            throw new RateLimitExceededException(
                    "Too many scan requests. Please try again in a while.");
        }

        validate(file);
        // Required, not optional: the whole point of this check is to catch
        // exactly the failure mode confidence alone missed (a confirmed
        // production case — 99.71% confidence, wrong crop AND wrong health
        // status). An optional field would let users skip the safety net.
        String normalizedDeclaredCrop = validateDeclaredCrop(declaredCrop);

        String imageUrl = storageProvider.store(file);
        AiPredictionResponse prediction = aiServiceClient.predict(file);

        boolean isUncertain = prediction.confidenceScore().compareTo(UNCERTAINTY_THRESHOLD) < 0;
        boolean isHealthy = HEALTHY_LABEL.equals(prediction.diseaseName());
        // Independent of confidence/uncertainty on purpose — a mismatch is a
        // mismatch even at 99.71% confidence, which is exactly what the
        // confirmed production case proved confidence alone can't catch.
        boolean cropMismatch = !normalizedDeclaredCrop.equalsIgnoreCase(prediction.cropName());
        String recommendation = recommendationTextProvider.recommendationFor(prediction.classLabel(), isHealthy);
        recommendation = recommendationTextProvider.applyUncertaintyCaveat(recommendation, isUncertain);

        CropScan scan = new CropScan();
        scan.setUserId(userId);
        scan.setDeclaredCrop(normalizedDeclaredCrop);
        scan.setImageUrl(imageUrl);
        scan.setCropName(prediction.cropName());
        scan.setDiseaseName(prediction.diseaseName());
        scan.setConfidenceScore(prediction.confidenceScore());
        scan.setSeverity(prediction.severity());
        scan.setRecommendation(recommendation);
        scan.setModelVersion(prediction.modelVersion());
        scan.setUncertain(isUncertain);
        scan.setCropMismatch(cropMismatch);

        CropScan saved = cropScanRepository.save(scan);
        return cropScanMapper.toResponse(saved);
    }

    @Override
    public List<SupportedCropResponse> getSupportedCrops() {
        return supportedCropsProvider.getSupportedCrops();
    }

    private String validateDeclaredCrop(String declaredCrop) {
        if (declaredCrop == null || declaredCrop.isBlank()) {
            throw new ValidationException("Please select which crop you're photographing");
        }
        if (!supportedCropsProvider.isKnownCrop(declaredCrop)) {
            throw new ValidationException("Unrecognized crop: " + declaredCrop);
        }
        return SupportedCropsProvider.normalize(declaredCrop);
    }

    @Override
    public PaginatedResponse<CropScanSummaryResponse> listMyScans(int page, int size) {
        Long userId = currentUserProvider.getCurrentUserId();
        Page<CropScan> scans = cropScanRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
        List<CropScanSummaryResponse> summaries = scans.getContent().stream()
                .map(cropScanMapper::toSummaryResponse)
                .toList();
        return PaginatedResponse.of(summaries, scans);
    }

    @Override
    public CropScanResponse getScanDetail(Long scanId) {
        return cropScanMapper.toResponse(getOwnedScanOrThrow(scanId));
    }

    @Override
    @Transactional
    public void deleteScan(Long scanId) {
        // Real delete, not the soft-delete (is_active=false) pattern used for
        // orders/bookings elsewhere — this is personal scan history, not a
        // financial/business record that needs to remain queryable after
        // removal (Section 5.3 of the feature spec).
        CropScan scan = getOwnedScanOrThrow(scanId);
        cropScanRepository.delete(scan);
    }

    @Override
    public CropScanReport getReport(Long scanId) {
        CropScan scan = getOwnedScanOrThrow(scanId);
        byte[] pdfBytes = cropScanReportService.generateReport(scan);
        return new CropScanReport(pdfBytes, buildFilename(scan));
    }

    /**
     * Same 404-not-403-on-mismatch convention as {@code AddressServiceImpl}
     * (Module 4) — a caller can't distinguish "doesn't exist" from "exists
     * but isn't yours."
     */
    private CropScan getOwnedScanOrThrow(Long scanId) {
        CropScan scan = cropScanRepository.findById(scanId)
                .filter(CropScan::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Scan not found"));

        Long userId = currentUserProvider.getCurrentUserId();
        if (!scan.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Scan not found");
        }
        return scan;
    }

    /**
     * Mirrors {@code MediaServiceImpl.validate} (Module 5) exactly — same
     * magic-byte check via the now-shared {@link ImageContentTypeDetector} —
     * plus a size cap and minimum-dimension check specific to this feature
     * (8MB / 32px, per Section 5.4 of the feature spec).
     */
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("An image is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException("Image exceeds the maximum allowed size of 8MB");
        }

        String declaredContentType = file.getContentType();
        if (declaredContentType == null || !ALLOWED_CONTENT_TYPES.contains(declaredContentType)) {
            throw new ValidationException("Only JPEG, PNG, and WEBP images are allowed");
        }

        String detectedContentType = ImageContentTypeDetector.detect(file);
        if (detectedContentType == null || !detectedContentType.equals(declaredContentType)) {
            throw new ValidationException("File content does not match its declared image type");
        }

        checkMinimumDimensions(file);
    }

    private void checkMinimumDimensions(MultipartFile file) {
        BufferedImage image;
        try {
            image = ImageIO.read(file.getInputStream());
        } catch (IOException ex) {
            throw new ValidationException("Could not read image file");
        }
        if (image == null || image.getWidth() < MIN_DIMENSION_PX || image.getHeight() < MIN_DIMENSION_PX) {
            throw new ValidationException(
                    "Image is too small to analyze (minimum " + MIN_DIMENSION_PX + "x" + MIN_DIMENSION_PX + "px)");
        }
    }

    private String buildFilename(CropScan scan) {
        String slug = (scan.getCropName() + "-" + scan.getDiseaseName())
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return "crop-scan-" + scan.getId() + (slug.isBlank() ? "" : "-" + slug) + ".pdf";
    }
}
