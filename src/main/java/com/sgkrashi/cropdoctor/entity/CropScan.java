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

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "crop_name", nullable = false, length = 100)
    private String cropName;

    @Column(name = "disease_name", nullable = false, length = 150)
    private String diseaseName;

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "severity", length = 50)
    private String severity;

    @Column(name = "recommendation", nullable = false, columnDefinition = "TEXT")
    private String recommendation;

    @Column(name = "model_version", nullable = false, length = 100)
    private String modelVersion;

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

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public boolean isUncertain() {
        return uncertain;
    }

    public void setUncertain(boolean uncertain) {
        this.uncertain = uncertain;
    }
}
