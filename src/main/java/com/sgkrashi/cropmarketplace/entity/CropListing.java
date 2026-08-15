package com.sgkrashi.cropmarketplace.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A crop batch listed for sale. {@code farmerId} is reserved (nullable FK to
 * {@code users}, per the Module 7 architecture note) for Module 20's Farmer
 * Portal — every listing in this module is Admin-curated seed data with
 * {@code farmerId = NULL}.
 */
@Entity
@Table(name = "crop_listings")
public class CropListing extends BaseEntity {

    @Column(name = "farmer_id")
    private Long farmerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crop_category_id", nullable = false)
    private CropCategory category;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 220)
    private String slug;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "quantity_available", nullable = false)
    private int quantityAvailable;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "harvest_date", nullable = false)
    private LocalDate harvestDate;

    @Column(name = "is_organic_certified", nullable = false)
    private boolean organicCertified;

    /** Denormalized from {@code reviews} (Module 12) — see {@code Product.avgRating}'s Javadoc for why. */
    @Column(name = "avg_rating", precision = 3, scale = 2)
    private BigDecimal avgRating;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    public Long getFarmerId() {
        return farmerId;
    }

    public void setFarmerId(Long farmerId) {
        this.farmerId = farmerId;
    }

    public CropCategory getCategory() {
        return category;
    }

    public void setCategory(CropCategory category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getQuantityAvailable() {
        return quantityAvailable;
    }

    public void setQuantityAvailable(int quantityAvailable) {
        this.quantityAvailable = quantityAvailable;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public LocalDate getHarvestDate() {
        return harvestDate;
    }

    public void setHarvestDate(LocalDate harvestDate) {
        this.harvestDate = harvestDate;
    }

    public boolean isOrganicCertified() {
        return organicCertified;
    }

    public void setOrganicCertified(boolean organicCertified) {
        this.organicCertified = organicCertified;
    }

    public BigDecimal getAvgRating() {
        return avgRating;
    }

    public void setAvgRating(BigDecimal avgRating) {
        this.avgRating = avgRating;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(int reviewCount) {
        this.reviewCount = reviewCount;
    }
}
