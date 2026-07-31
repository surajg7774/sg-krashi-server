package com.sgkrashi.productstore.service.impl;

import com.sgkrashi.audit.AuditActions;
import com.sgkrashi.audit.service.AuditLogService;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.common.util.SlugUtil;
import com.sgkrashi.media.dto.response.MediaAssetResponse;
import com.sgkrashi.media.entity.MediaAsset;
import com.sgkrashi.media.mapper.MediaAssetMapper;
import com.sgkrashi.media.repository.MediaAssetRepository;
import com.sgkrashi.productstore.dto.request.ProductAdminRequest;
import com.sgkrashi.productstore.dto.request.ProductFilterRequest;
import com.sgkrashi.productstore.dto.response.ProductDetailResponse;
import com.sgkrashi.productstore.dto.response.ProductSummaryResponse;
import com.sgkrashi.productstore.entity.Product;
import com.sgkrashi.productstore.entity.ProductCategory;
import com.sgkrashi.productstore.mapper.ProductMapper;
import com.sgkrashi.productstore.repository.ProductCategoryRepository;
import com.sgkrashi.productstore.repository.ProductRepository;
import com.sgkrashi.productstore.service.ProductService;
import com.sgkrashi.productstore.specification.ProductSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    private static final String PRODUCT_OWNER_TYPE = "PRODUCT";
    private static final String ENTITY_TYPE_PRODUCT = "PRODUCT";
    private static final int MAX_RELATED_PRODUCTS = 6;

    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final ProductMapper productMapper;
    private final MediaAssetMapper mediaAssetMapper;
    private final AuditLogService auditLogService;

    public ProductServiceImpl(
            ProductRepository productRepository,
            ProductCategoryRepository productCategoryRepository,
            MediaAssetRepository mediaAssetRepository,
            ProductMapper productMapper,
            MediaAssetMapper mediaAssetMapper,
            AuditLogService auditLogService
    ) {
        this.productRepository = productRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.productMapper = productMapper;
        this.mediaAssetMapper = mediaAssetMapper;
        this.auditLogService = auditLogService;
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
        return buildDetailResponse(resolveProduct(idOrSlug));
    }

    /** Unlike {@link #getProductDetail}, takes an already-resolved entity — used by the Admin create/update methods, which must return a detail response even for a just-deactivated (isActive=false) product that {@link #resolveProduct} would 404 on. */
    private ProductDetailResponse buildDetailResponse(Product product) {
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

    @Override
    @Transactional
    public ProductDetailResponse createProduct(ProductAdminRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        // The single point the mutation actually commits — confirms Module
        // 15's own note: this really was a clean one-line addition, same
        // shape as Module 13's event-publish hooks.
        Product saved = productRepository.save(product);
        ProductDetailResponse after = buildDetailResponse(saved);
        auditLogService.record(AuditActions.PRODUCT_CREATED, ENTITY_TYPE_PRODUCT, saved.getId(), null, after);
        return after;
    }

    @Override
    @Transactional
    public ProductDetailResponse updateProduct(Long id, ProductAdminRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        ProductDetailResponse before = buildDetailResponse(product);
        applyRequest(product, request);
        Product saved = productRepository.save(product);
        ProductDetailResponse after = buildDetailResponse(saved);
        auditLogService.record(AuditActions.PRODUCT_UPDATED, ENTITY_TYPE_PRODUCT, id, before, after);
        return after;
    }

    @Override
    @Transactional
    public void deactivateProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        ProductDetailResponse before = buildDetailResponse(product);
        product.setActive(false);
        Product saved = productRepository.save(product);
        auditLogService.record(AuditActions.PRODUCT_DEACTIVATED, ENTITY_TYPE_PRODUCT, id, before, buildDetailResponse(saved));
    }

    @Override
    public PaginatedResponse<ProductSummaryResponse> listProductsForAdmin(String search, int page, int size) {
        Specification<Product> spec = Specification.allOf(ProductSpecifications.nameContains(search));

        Pageable pageable = PageRequest.of(Math.max(page, 0), size > 0 ? size : 20, Sort.by(Sort.Direction.ASC, "name"));
        Page<Product> productPage = productRepository.findAll(spec, pageable);

        List<Long> productIds = productPage.getContent().stream().map(Product::getId).toList();
        Map<Long, String> thumbnailsByProductId = batchThumbnails(productIds);

        List<ProductSummaryResponse> items = productPage.getContent().stream()
                .map(product -> productMapper.toSummary(product, thumbnailsByProductId.get(product.getId())))
                .toList();

        return PaginatedResponse.of(items, productPage);
    }

    @Override
    public ProductDetailResponse getProductForAdmin(Long id) {
        Product product = productRepository.findWithCategoryById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return buildDetailResponse(product);
    }

    private void applyRequest(Product product, ProductAdminRequest request) {
        ProductCategory category = productCategoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Product category not found"));

        String slug = (request.slug() == null || request.slug().isBlank())
                ? SlugUtil.uniqueSlugFrom(request.name(), candidate -> !candidate.equals(product.getSlug()) && productRepository.existsBySlug(candidate))
                : request.slug();

        product.setCategory(category);
        product.setName(request.name());
        product.setSlug(slug);
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQty(request.stockQty());
        product.setOrganicCertified(request.isOrganicCertified());
        product.setActive(request.isActive());
    }
}
