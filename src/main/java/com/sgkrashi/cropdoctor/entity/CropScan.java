package com.sgkrashi.cropdoctor.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * One AI Crop Doctor scan result. Ownership is always resolved from
 * {@link #userId} against the authenticated user — never from a
 * client-supplied identifier — before any read or mutation is allowed, same
 * as {@code Address} (Module 4).
 *
 * <p>Phase 1 (Gemini) added {@link #reportJson} etc. alongside the original
 * flat fields rather than replacing them — old scans (the fixed-class
 * classifier, one field each) and new scans (Gemini, full structured report
 * in {@link #reportJson}) coexist in this same table. {@code
 * CropScanMapper} is where the two shapes get normalized into one response;
 * this entity doesn't try to hide the difference.
 */
@Entity
@Table(name = "crop_scans")
public class CropScan extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // What the user said they were photographing — compared against the
    // model's predicted crop to catch the failure mode confidence alone
    // can't: a confident (99.71% in the confirmed production case) wrong
    // crop AND wrong health status on an out-of-distribution photo.
    @Column(name = "declared_crop", length = 100)
    private String declaredCrop;

    @Column(name = "language", length = 10)
    private String language;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    // JSON array of all submitted image URLs (1-3) — imageUrl above always
    // holds the first one too, for old code paths / thumbnails that only
    // need one image.
    @Column(name = "image_urls", columnDefinition = "TEXT")
    private String imageUrls;

    @Column(name = "crop_name", nullable = false, length = 100)
    private String cropName;

    @Column(name = "disease_name", length = 150)
    private String diseaseName;

    // Real calibrated probability from the old classifier only — never
    // populated for Gemini scans (see confidenceBand). Never rendered as a
    // percentage anywhere in the UI.
    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "confidence_band", length = 20)
    private String confidenceBand;

    @Column(name = "severity", length = 50)
    private String severity;

    @Column(name = "recommendation", columnDefinition = "TEXT")
    private String recommendation;

    // Full structured CropAnalysisResult as JSON — the source of truth for
    // Gemini-era scans. Null for scans predating Phase 1.
    @Column(name = "report_json", columnDefinition = "LONGTEXT")
    private String reportJson;

    @Column(name = "model_version", nullable = false, length = 100)
    private String modelVersion;

    @Column(name = "provider_name", length = 50)
    private String providerName;

    @Column(name = "is_uncertain", nullable = false)
    private boolean uncertain;

    // Independent of `uncertain` — a result can be mismatched-but-confident,
    // uncertain-but-crop-matched, both, or neither. Never derive one from
    // the other.
    @Column(name = "crop_mismatch", nullable = false)
    private boolean cropMismatch;

    public String getDeclaredCrop() {
        return declaredCrop;
    }

    public void setDeclaredCrop(String declaredCrop) {
        this.declaredCrop = declaredCrop;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean isCropMismatch() {
        return cropMismatch;
    }

    public void setCropMismatch(boolean cropMismatch) {
        this.cropMismatch = cropMismatch;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(String imageUrls) {
        this.imageUrls = imageUrls;
    }

    public String getCropName() {
        return cropName;
    }

    public void setCropName(String cropName) {
        this.cropName = cropName;
    }

    public String getDiseaseName() {
        return diseaseName;
    }

    public void setDiseaseName(String diseaseName) {
        this.diseaseName = diseaseName;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getConfidenceBand() {
        return confidenceBand;
    }

    public void setConfidenceBand(String confidenceBand) {
        this.confidenceBand = confidenceBand;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public String getReportJson() {
        return reportJson;
    }

    public void setReportJson(String reportJson) {
        this.reportJson = reportJson;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public boolean isUncertain() {
        return uncertain;
    }

    public void setUncertain(boolean uncertain) {
        this.uncertain = uncertain;
    }
}
