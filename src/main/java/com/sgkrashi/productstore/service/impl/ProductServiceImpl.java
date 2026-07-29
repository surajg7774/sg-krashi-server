package com.sgkrashi.productstore.service.impl;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.media.dto.response.MediaAssetResponse;
import com.sgkrashi.media.entity.MediaAsset;
import com.sgkrashi.media.mapper.MediaAssetMapper;
import com.sgkrashi.media.repository.MediaAssetRepository;
import com.sgkrashi.productstore.dto.request.ProductFilterRequest;
import com.sgkrashi.productstore.dto.response.ProductDetailResponse;
import com.sgkrashi.productstore.dto.response.ProductSummaryResponse;
import com.sgkrashi.productstore.entity.Product;
import com.sgkrashi.productstore.mapper.ProductMapper;
import com.sgkrashi.productstore.repository.ProductRepository;
import com.sgkrashi.productstore.service.ProductService;
import com.sgkrashi.productstore.specification.ProductSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    private static final String PRODUCT_OWNER_TYPE = "PRODUCT";
    private static final int MAX_RELATED_PRODUCTS = 6;

    private final ProductRepository productRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final ProductMapper productMapper;
    private final MediaAssetMapper mediaAssetMapper;

    public ProductServiceImpl(
            ProductRepository productRepository,
            MediaAssetRepository mediaAssetRepository,
            ProductMapper productMapper,
            MediaAssetMapper mediaAssetMapper
    ) {
        this.productRepository = productRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.productMapper = productMapper;
        this.mediaAssetMapper = mediaAssetMapper;
    }

    /**
     * Composes one {@link Specification} per filter criterion via
     * {@code Specification.allOf(...)} — see {@code ProductSpecifications} —
     * rather than branching on each filter in a single method. Thumbnails for
     * the page are fetched in a single batched query keyed by product ID, not
     * one query per row.
     */
    @Override
    public PaginatedResponse<ProductSummaryResponse> listProducts(ProductFilterRequest filter) {
        Specification<Product> spec = Specification.allOf(
                ProductSpecifications.isActive(),
                ProductSpecifications.hasCategory(filter.categoryId()),
                ProductSpecifications.priceGreaterThanOrEqual(filter.minPrice()),
                ProductSpecifications.priceLessThanOrEqual(filter.maxPrice()),
                ProductSpecifications.isOrganicCertified(filter.organicOnly()),
                ProductSpecifications.nameContains(filter.search()));

        Pageable pageable = PageRequest.of(
                Math.max(filter.page(), 0),
                filter.size() > 0 ? filter.size() : 20,
                Sort.by(Sort.Direction.ASC, "name"));

        Page<Product> productPage = productRepository.findAll(spec, pageable);

        List<Long> productIds = productPage.getContent().stream().map(Product::getId).toList();
        Map<Long, String> thumbnailsByProductId = batchThumbnails(productIds);

        List<ProductSummaryResponse> items = productPage.getContent().stream()
                .map(product -> productMapper.toSummary(product, thumbnailsByProductId.get(product.getId())))
                .toList();

        return PaginatedResponse.of(items, productPage);
    }

    @Override
    public ProductDetailResponse getProductDetail(String idOrSlug) {
        Product product = resolveProduct(idOrSlug);

        List<MediaAssetResponse> media = mediaAssetRepository
                .findByOwnerTypeAndOwnerIdOrderBySortOrderAsc(PRODUCT_OWNER_TYPE, product.getId()).stream()
                .map(mediaAssetMapper::toResponse)
                .toList();

        List<ProductSummaryResponse> relatedProducts = buildRelatedProducts(product);

        return productMapper.toDetail(product, media, relatedProducts);
    }

    private List<ProductSummaryResponse> buildRelatedProducts(Product product) {
        if (product.getCategory() == null) {
            return List.of();
        }
        List<Product> related = productRepository.findTop6ByCategoryIdAndIsActiveTrueAndIdNot(
                product.getCategory().getId(), product.getId());

        List<Long> relatedIds = related.stream().map(Product::getId).toList();
        Map<Long, String> thumbnailsByProductId = batchThumbnails(relatedIds);

        return related.stream()
                .limit(MAX_RELATED_PRODUCTS)
                .map(p -> productMapper.toSummary(p, thumbnailsByProductId.get(p.getId())))
                .toList();
    }

    private Map<Long, String> batchThumbnails(List<Long> productIds) {
        return mediaAssetRepository
                .findByOwnerTypeAndOwnerIdInOrderBySortOrderAsc(PRODUCT_OWNER_TYPE, productIds).stream()
                .collect(Collectors.toMap(
                        MediaAsset::getOwnerId,
                        MediaAsset::getUrl,
                        (first, second) -> first));
    }

    private Product resolveProduct(String idOrSlug) {
        return parseId(idOrSlug)
                .flatMap(productRepository::findByIdAndIsActiveTrue)
                .or(() -> productRepository.findBySlugAndIsActiveTrue(idOrSlug))
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + idOrSlug));
    }

    private Optional<Long> parseId(String idOrSlug) {
        try {
            return Optional.of(Long.valueOf(idOrSlug));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
