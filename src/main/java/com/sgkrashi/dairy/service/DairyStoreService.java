package com.sgkrashi.dairy.service;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.dairy.config.DairyProperties;
import com.sgkrashi.dairy.dto.DairyDtos.DairyConfigResponse;
import com.sgkrashi.dairy.dto.DairyDtos.DairyDetailsResponse;
import com.sgkrashi.dairy.dto.DairyDtos.DairyProductResponse;
import com.sgkrashi.dairy.dto.DairyDtos.DeliveryOptionsResponse;
import com.sgkrashi.media.entity.MediaAsset;
import com.sgkrashi.media.repository.MediaAssetRepository;
import com.sgkrashi.productstore.dto.response.ProductSummaryResponse;
import com.sgkrashi.productstore.entity.Product;
import com.sgkrashi.productstore.mapper.ProductMapper;
import com.sgkrashi.productstore.repository.ProductRepository;
import com.sgkrashi.productstore.specification.ProductSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** What the public Dairy page needs: switches, the live dairy products and the delivery options. */
@Service
public class DairyStoreService {

    private static final String PRODUCT_OWNER_TYPE = "PRODUCT";

    private final ProductRepository productRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final ProductMapper productMapper;
    private final DairyCatalogService catalogService;
    private final ProductDairyDetailsService detailsService;
    private final DeliveryRulesService rulesService;
    private final DairyProperties properties;

    public DairyStoreService(
            ProductRepository productRepository,
            MediaAssetRepository mediaAssetRepository,
            ProductMapper productMapper,
            DairyCatalogService catalogService,
            ProductDairyDetailsService detailsService,
            DeliveryRulesService rulesService,
            DairyProperties properties
    ) {
        this.productRepository = productRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.productMapper = productMapper;
        this.catalogService = catalogService;
        this.detailsService = detailsService;
        this.rulesService = rulesService;
        this.properties = properties;
    }

    public DairyConfigResponse config() {
        return new DairyConfigResponse(properties.subscriptionsEnabled(), rulesService.isRestricted(), !rulesService.activeSlots().isEmpty());
    }

    public PaginatedResponse<DairyProductResponse> listProducts(int page, int size) {
        Set<Long> dairyIds = catalogService.dairyCategoryIds();
        Pageable pageable = PageRequest.of(Math.max(page, 0), size > 0 ? Math.min(size, 50) : 12, Sort.by(Sort.Direction.ASC, "name"));
        Specification<Product> spec = Specification.allOf(ProductSpecifications.isActive(), ProductSpecifications.hasCategoryIn(dairyIds));
        Page<Product> result = dairyIds.isEmpty() ? Page.empty(pageable) : productRepository.findAll(spec, pageable);

        List<Long> ids = result.getContent().stream().map(Product::getId).toList();
        Map<Long, String> thumbnails = ids.isEmpty() ? Map.of() : mediaAssetRepository
                .findByOwnerTypeAndOwnerIdInOrderBySortOrderAsc(PRODUCT_OWNER_TYPE, ids).stream()
                .collect(Collectors.toMap(MediaAsset::getOwnerId, MediaAsset::getUrl, (first, second) -> first));
        Map<Long, DairyDetailsResponse> details = detailsService.findAll(ids);

        List<DairyProductResponse> items = result.getContent().stream().map(product -> {
            ProductSummaryResponse summary = productMapper.toSummary(product, thumbnails.get(product.getId()));
            return new DairyProductResponse(summary, details.get(product.getId()));
        }).toList();
        return PaginatedResponse.of(items, result);
    }

    /** Ids of every active dairy product; the checkout page uses it to know whether a cart contains dairy. */
    public List<Long> dairyProductIds() {
        Set<Long> dairyIds = catalogService.dairyCategoryIds();
        return dairyIds.isEmpty() ? List.of() : productRepository.findActiveIdsByCategoryIds(dairyIds);
    }

    public DeliveryOptionsResponse deliveryOptions(String pincode) {
        return rulesService.options(pincode);
    }
}
