package com.sgkrashi.cropmarketplace.specification;

import com.sgkrashi.cropmarketplace.entity.CropListing;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Mirrors {@code ProductSpecifications}' structure exactly: one static method
 * per filter criterion, {@code null} predicate when the filter value is
 * absent, composed via {@code Specification.allOf(...)} in
 * {@code CropListingServiceImpl}.
 *
 * <p>One genuine deviation from a straight copy: {@code hasCropType} filters
 * against the listing's {@code category} (name or slug) rather than a
 * separate "crop type" column. This module's category set (Grains, Pulses,
 * Vegetables) IS the crop-type facet the frontend filters by — adding a
 * second, separately-seeded "crop type" string field alongside category
 * would model the same real-world concept twice for no behavioral gain. See
 * the Module 7 report for the full reasoning.
 */
public final class CropListingSpecifications {

    private CropListingSpecifications() {
    }

    public static Specification<CropListing> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("isActive"));
    }

    public static Specification<CropListing> hasCropType(String cropType) {
        return (root, query, cb) -> (cropType == null || cropType.isBlank())
                ? null
                : cb.equal(cb.lower(root.get("category").get("slug")), cropType.toLowerCase());
    }

    public static Specification<CropListing> priceGreaterThanOrEqual(BigDecimal minPrice) {
        return (root, query, cb) -> minPrice == null
                ? null
                : cb.greaterThanOrEqualTo(root.get("unitPrice"), minPrice);
    }

    public static Specification<CropListing> priceLessThanOrEqual(BigDecimal maxPrice) {
        return (root, query, cb) -> maxPrice == null
                ? null
                : cb.lessThanOrEqualTo(root.get("unitPrice"), maxPrice);
    }

    public static Specification<CropListing> isOrganicCertified(Boolean organicOnly) {
        return (root, query, cb) -> (organicOnly == null || !organicOnly)
                ? null
                : cb.isTrue(root.get("organicCertified"));
    }

    public static Specification<CropListing> harvestDateFrom(LocalDate from) {
        return (root, query, cb) -> from == null
                ? null
                : cb.greaterThanOrEqualTo(root.get("harvestDate"), from);
    }

    public static Specification<CropListing> harvestDateTo(LocalDate to) {
        return (root, query, cb) -> to == null
                ? null
                : cb.lessThanOrEqualTo(root.get("harvestDate"), to);
    }

    /** Module 20 — Farmer-scoped listing management; {@code null} skips the filter (Admin's unscoped listing view). */
    public static Specification<CropListing> belongsToFarmer(Long farmerId) {
        return (root, query, cb) -> farmerId == null
                ? null
                : cb.equal(root.get("farmerId"), farmerId);
    }

    public static Specification<CropListing> nameContains(String search) {
        return (root, query, cb) -> (search == null || search.isBlank())
                ? null
                : cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%");
    }
}
