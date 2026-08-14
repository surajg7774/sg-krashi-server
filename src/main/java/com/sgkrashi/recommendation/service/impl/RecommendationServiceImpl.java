package com.sgkrashi.recommendation.service.impl;

import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.cropmarketplace.entity.CropListing;
import com.sgkrashi.cropmarketplace.repository.CropListingRepository;
import com.sgkrashi.media.entity.MediaAsset;
import com.sgkrashi.media.repository.MediaAssetRepository;
import com.sgkrashi.productstore.entity.Product;
import com.sgkrashi.productstore.repository.ProductRepository;
import com.sgkrashi.recommendation.dto.response.RecommendationResponse;
import com.sgkrashi.recommendation.repository.RecommendationQueryRepository;
import com.sgkrashi.recommendation.service.RecommendationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Two deliberately simple, explainable techniques (Recommendation System
 * spec, Section 2.1) — no matrix factorization, no trained model:
 * <ul>
 *   <li>{@link #getSimilarItems} — content-based: same category, similar
 *   price band, ranked by rating.</li>
 *   <li>{@link #getFrequentlyBoughtWith} / {@link #getForYou} — item-based
 *   collaborative filtering via a plain co-occurrence count over real order
 *   history, in {@link RecommendationQueryRepository}.</li>
 * </ul>
 */
@Service
public class RecommendationServiceImpl implements RecommendationService {

    private static final String PRODUCT_OWNER_TYPE = "PRODUCT";
    private static final String CROP_LISTING_OWNER_TYPE = "CROP_LISTING";
    private static final String TARGET_TYPE_PRODUCT = "PRODUCT";
    private static final String TARGET_TYPE_CROP_LISTING = "CROP_LISTING";
    private static final int DEFAULT_SIMILAR_LIMIT = 6;
    private static final int DEFAULT_FOR_YOU_LIMIT = 8;
    // "Similar price range" — a symmetric band around the target's own price
    // (half to double), not a fixed absolute amount, so it behaves sensibly
    // whether the target is a ₹50 spice packet or a ₹5,000 item.
    private static final BigDecimal PRICE_BAND_LOWER_FACTOR = new BigDecimal("0.5");
    private static final BigDecimal PRICE_BAND_UPPER_FACTOR = new BigDecimal("2.0");
    // Sentinel for JPQL's "NOT IN :excludeIds" when there's genuinely nothing
    // to exclude — an empty IN-list is invalid JPQL, and no real id is ever -1.
    private static final List<Long> NO_EXCLUSIONS = List.of(-1L);

    private final ProductRepository productRepository;
    private final CropListingRepository cropListingRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final RecommendationQueryRepository recommendationQueryRepository;
    private final CurrentUserProvider currentUserProvider;

    public RecommendationServiceImpl(
            ProductRepository productRepository,
            CropListingRepository cropListingRepository,
            MediaAssetRepository mediaAssetRepository,
            RecommendationQueryRepository recommendationQueryRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.productRepository = productRepository;
        this.cropListingRepository = cropListingRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.recommendationQueryRepository = recommendationQueryRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public RecommendationResponse getSimilarItems(String targetType, Long targetId, int limit) {
        int effectiveLimit = limit > 0 ? limit : DEFAULT_SIMILAR_LIMIT;

        if (TARGET_TYPE_PRODUCT.equals(targetType)) {
            Product target = productRepository.findByIdAndIsActiveTrue(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + targetId));
            if (target.getCategory() == null) {
                return new RecommendationResponse(List.of());
            }
            BigDecimal minPrice = target.getPrice().multiply(PRICE_BAND_LOWER_FACTOR);
            BigDecimal maxPrice = target.getPrice().multiply(PRICE_BAND_UPPER_FACTOR);
            List<Product> similar = productRepository.findSimilarByCategoryAndPriceRange(
                    target.getCategory().getId(), target.getId(), minPrice, maxPrice, PageRequest.of(0, effectiveLimit));
            return new RecommendationResponse(toProductItems(similar));
        }

        if (TARGET_TYPE_CROP_LISTING.equals(targetType)) {
            CropListing target = cropListingRepository.findByIdAndIsActiveTrue(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Crop listing not found: " + targetId));
            if (target.getCategory() == null) {
                return new RecommendationResponse(List.of());
            }
            BigDecimal minPrice = target.getUnitPrice().multiply(PRICE_BAND_LOWER_FACTOR);
            BigDecimal maxPrice = target.getUnitPrice().multiply(PRICE_BAND_UPPER_FACTOR);
            List<CropListing> similar = cropListingRepository.findSimilarByCategoryAndPriceRange(
                    target.getCategory().getId(), target.getId(), minPrice, maxPrice, PageRequest.of(0, effectiveLimit));
            return new RecommendationResponse(toCropListingItems(similar));
        }

        throw new BusinessRuleException("Unknown targetType: " + targetType);
    }

    @Override
    public RecommendationResponse getFrequentlyBoughtWith(Long productId, int limit) {
        int effectiveLimit = limit > 0 ? limit : DEFAULT_SIMILAR_LIMIT;
        List<Object[]> rows = recommendationQueryRepository.findFrequentlyBoughtWithProductIds(productId, effectiveLimit);
        if (rows.isEmpty()) {
            return new RecommendationResponse(List.of());
        }

        // The query already ranked these by co-occurrence count — preserve
        // that order rather than letting findAllById return its own order.
        List<Long> orderedIds = rows.stream().map(row -> toLong(row[0])).toList();
        Map<Long, Product> byId = productRepository.findAllById(orderedIds).stream()
                .filter(Product::isActive)
                .collect(Collectors.toMap(Product::getId, p -> p));
        List<Product> ordered = orderedIds.stream().map(byId::get).filter(Objects::nonNull).toList();
        return new RecommendationResponse(toProductItems(ordered));
    }

    @Override
    public RecommendationResponse getForYou(int limit) {
        int effectiveLimit = limit > 0 ? limit : DEFAULT_FOR_YOU_LIMIT;
        Long userId = currentUserProvider.getCurrentUserId();

        List<Long> purchasedProductCategoryIds = recommendationQueryRepository.findPurchasedProductCategoryIds(userId);
        List<Long> purchasedCropCategoryIds = recommendationQueryRepository.findPurchasedCropCategoryIds(userId);
        List<Long> excludeProductIds = orSentinel(recommendationQueryRepository.findPurchasedProductIds(userId));
        List<Long> excludeCropListingIds = orSentinel(recommendationQueryRepository.findPurchasedCropListingIds(userId));

        boolean hasHistory = !purchasedProductCategoryIds.isEmpty() || !purchasedCropCategoryIds.isEmpty();
        int perType = Math.max(1, effectiveLimit / 2);

        List<Product> products;
        List<CropListing> cropListings;
        if (hasHistory) {
            products = purchasedProductCategoryIds.isEmpty() ? List.of()
                    : productRepository.findTopRatedInCategories(purchasedProductCategoryIds, excludeProductIds, PageRequest.of(0, perType));
            cropListings = purchasedCropCategoryIds.isEmpty() ? List.of()
                    : cropListingRepository.findTopRatedInCategories(purchasedCropCategoryIds, excludeCropListingIds, PageRequest.of(0, perType));
        } else {
            // No order history at all — degrade gracefully to platform-wide
            // top-rated items rather than returning nothing (spec requirement).
            products = productRepository.findTopRatedOverall(excludeProductIds, PageRequest.of(0, perType));
            cropListings = cropListingRepository.findTopRatedOverall(excludeCropListingIds, PageRequest.of(0, perType));
        }

        List<RecommendationResponse.RecommendationItem> items = new ArrayList<>();
        items.addAll(toProductItems(products));
        items.addAll(toCropListingItems(cropListings));
        return new RecommendationResponse(items.stream().limit(effectiveLimit).toList());
    }

    private List<Long> orSentinel(List<Long> ids) {
        return ids.isEmpty() ? NO_EXCLUSIONS : ids;
    }

    private List<RecommendationResponse.RecommendationItem> toProductItems(List<Product> products) {
        Map<Long, String> thumbnails = batchThumbnails(PRODUCT_OWNER_TYPE, products.stream().map(Product::getId).toList());
        return products.stream()
                .map(p -> new RecommendationResponse.RecommendationItem(
                        p.getId(), TARGET_TYPE_PRODUCT, p.getName(), p.getSlug(), p.getPrice(),
                        thumbnails.get(p.getId()), p.getAvgRating(), p.getReviewCount()))
                .toList();
    }

    private List<RecommendationResponse.RecommendationItem> toCropListingItems(List<CropListing> listings) {
        Map<Long, String> thumbnails = batchThumbnails(CROP_LISTING_OWNER_TYPE, listings.stream().map(CropListing::getId).toList());
        return listings.stream()
                .map(c -> new RecommendationResponse.RecommendationItem(
                        c.getId(), TARGET_TYPE_CROP_LISTING, c.getName(), c.getSlug(), c.getUnitPrice(),
                        thumbnails.get(c.getId()), c.getAvgRating(), c.getReviewCount()))
                .toList();
    }

    private Map<Long, String> batchThumbnails(String ownerType, List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mediaAssetRepository.findByOwnerTypeAndOwnerIdInOrderBySortOrderAsc(ownerType, ids).stream()
                .collect(Collectors.toMap(MediaAsset::getOwnerId, MediaAsset::getUrl, (first, second) -> first));
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
