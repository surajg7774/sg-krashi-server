package com.sgkrashi.cropdoctor.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.RateLimitExceededException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.common.exception.ValidationException;
import com.sgkrashi.cropdoctor.dto.response.CropScanReport;
import com.sgkrashi.cropdoctor.dto.response.CropScanResponse;
import com.sgkrashi.cropdoctor.dto.response.CropScanSummaryResponse;
import com.sgkrashi.cropdoctor.dto.response.SupportedCropResponse;
import com.sgkrashi.cropdoctor.entity.CropScan;
import com.sgkrashi.cropdoctor.mapper.CropScanMapper;
import com.sgkrashi.cropdoctor.provider.ConfidenceBand;
import com.sgkrashi.cropdoctor.provider.CropAnalysisProvider;
import com.sgkrashi.cropdoctor.provider.CropAnalysisResult;
import com.sgkrashi.cropdoctor.ratelimit.AiCropDoctorRateLimiter;
import com.sgkrashi.cropdoctor.repository.CropScanRepository;
import com.sgkrashi.cropdoctor.service.CropDoctorService;
import com.sgkrashi.cropdoctor.service.CropScanReportService;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CropDoctorServiceImpl implements CropDoctorService {

    private static final long MAX_FILE_SIZE_BYTES = 8L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final int MIN_DIMENSION_PX = 32;
    private static final int MAX_IMAGES = 3;
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "hi", "mr", "gu");

    private final StorageProvider storageProvider;
    private final CropAnalysisProvider cropAnalysisProvider;
    private final CropScanRepository cropScanRepository;
    private final CropScanMapper cropScanMapper;
    private final CropScanReportService cropScanReportService;
    private final CurrentUserProvider currentUserProvider;
    private final AiCropDoctorRateLimiter rateLimiter;
    private final SupportedCropsProvider supportedCropsProvider;
    private final ObjectMapper objectMapper;

    public CropDoctorServiceImpl(
            StorageProvider storageProvider,
            CropAnalysisProvider cropAnalysisProvider,
            CropScanRepository cropScanRepository,
            CropScanMapper cropScanMapper,
            CropScanReportService cropScanReportService,
            CurrentUserProvider currentUserProvider,
            AiCropDoctorRateLimiter rateLimiter,
            SupportedCropsProvider supportedCropsProvider,
            ObjectMapper objectMapper
    ) {
        this.storageProvider = storageProvider;
        this.cropAnalysisProvider = cropAnalysisProvider;
        this.cropScanRepository = cropScanRepository;
        this.cropScanMapper = cropScanMapper;
        this.cropScanReportService = cropScanReportService;
        this.currentUserProvider = currentUserProvider;
        this.rateLimiter = rateLimiter;
        this.supportedCropsProvider = supportedCropsProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public CropScanResponse analyze(List<MultipartFile> files, String declaredCrop, String language) {
        Long userId = currentUserProvider.getCurrentUserId();

        if (!rateLimiter.tryConsume(String.valueOf(userId))) {
            throw new RateLimitExceededException("Too many scan requests. Please try again in a while.");
        }

        validateFiles(files);
        // Required, not optional: the whole point of this check is to catch
        // exactly the failure mode confidence alone missed (a confirmed
        // production case — 99.71% confidence, wrong crop AND wrong health
        // status). An optional field would let users skip the safety net.
        String normalizedDeclaredCrop = validateDeclaredCrop(declaredCrop);
        String normalizedLanguage = validateLanguage(language);

        List<String> imageUrls = files.stream().map(storageProvider::store).toList();

        CropAnalysisResult result = cropAnalysisProvider.analyze(files, normalizedDeclaredCrop, normalizedLanguage);

        CropScan scan = new CropScan();
        scan.setUserId(userId);
        scan.setDeclaredCrop(normalizedDeclaredCrop);
        scan.setLanguage(normalizedLanguage);
        scan.setImageUrl(imageUrls.get(0));
        scan.setImageUrls(writeJson(imageUrls));
        scan.setCropName(result.identifiedCrop());
        scan.setDiseaseName(result.problem());
        scan.setSeverity(result.severity() == null ? null : result.severity().name());
        scan.setModelVersion(result.providerModelVersion());
        scan.setProviderName(result.providerName());
        scan.setConfidenceBand(result.confidenceBand().name());
        scan.setReportJson(writeJson(result));
        // Independent of each other on purpose — a result can be
        // mismatched-but-confident, uncertain-but-crop-matched, both, or
        // neither. Never derive one from the other.
        scan.setUncertain(result.confidenceBand() == ConfidenceBand.LOW);
        scan.setCropMismatch(!result.cropMatchesDeclared());

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

    private String validateLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "en";
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_LANGUAGES.contains(normalized)) {
            throw new ValidationException("Unsupported language: " + language);
        }
        return normalized;
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

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ValidationException("At least one image is required");
        }
        if (files.size() > MAX_IMAGES) {
            throw new ValidationException("A maximum of " + MAX_IMAGES + " images is allowed per scan");
        }
        files.forEach(this::validate);
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
        String problem = scan.getDiseaseName() == null ? "healthy" : scan.getDiseaseName();
        String slug = (scan.getCropName() + "-" + problem)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return "crop-scan-" + scan.getId() + (slug.isBlank() ? "" : "-" + slug) + ".pdf";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize crop scan data", ex);
        }
    }
}
