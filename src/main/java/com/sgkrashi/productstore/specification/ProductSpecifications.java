package com.sgkrashi.productstore.specification;

import com.sgkrashi.productstore.entity.Product;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/**
 * One small {@link Specification} per filter criterion, composed with
 * {@code Specification.where(...).and(...)} in {@code ProductServiceImpl} —
 * not a single method with a growing if/else chain. Each builder returns a
 * {@code null} predicate when its filter value is absent, which Spring Data's
 * {@code and()} composition treats as "no constraint" and simply skips.
 *
 * <p>This is the pattern Module 7 (Crop Marketplace) copies: define one static
 * method per filterable field on the target entity, each following this same
 * null-means-skip shape, then compose them in the service layer in the order
 * the filters logically apply.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("isActive"));
    }

    public static Specification<Product> hasCategory(Long categoryId) {
        return (root, query, cb) -> categoryId == null
                ? null
                : cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Product> priceGreaterThanOrEqual(BigDecimal minPrice) {
        return (root, query, cb) -> minPrice == null
                ? null
                : cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<Product> priceLessThanOrEqual(BigDecimal maxPrice) {
        return (root, query, cb) -> maxPrice == null
                ? null
                : cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    public static Specification<Product> isOrganicCertified(Boolean organicOnly) {
        return (root, query, cb) -> (organicOnly == null || !organicOnly)
                ? null
                : cb.isTrue(root.get("organicCertified"));
    }

    public static Specification<Product> nameContains(String search) {
        return (root, query, cb) -> (search == null || search.isBlank())
                ? null
                : cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%");
    }
}
