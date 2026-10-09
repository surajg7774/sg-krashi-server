package com.sgkrashi.dairy.service;

import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.dairy.dto.DairyDtos.DairyDetailsRequest;
import com.sgkrashi.dairy.dto.DairyDtos.DairyDetailsResponse;
import com.sgkrashi.dairy.entity.ProductDairyDetails;
import com.sgkrashi.dairy.repository.ProductDairyDetailsRepository;
import com.sgkrashi.productstore.entity.Product;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads and writes the dairy facts of a product (unit, pack size, shelf life, storage). */
@Service
public class ProductDairyDetailsService {

    private final ProductDairyDetailsRepository repository;
    private final DairyCatalogService catalogService;

    public ProductDairyDetailsService(ProductDairyDetailsRepository repository, DairyCatalogService catalogService) {
        this.repository = repository;
        this.catalogService = catalogService;
    }

    public Optional<DairyDetailsResponse> find(Long productId) {
        return repository.findById(productId).map(ProductDairyDetailsService::toResponse);
    }

    public Map<Long, DairyDetailsResponse> findAll(Collection<Long> productIds) {
        Map<Long, DairyDetailsResponse> byProduct = new HashMap<>();
        if (productIds.isEmpty()) {
            return byProduct;
        }
        List<ProductDairyDetails> rows = repository.findByProductIdIn(productIds);
        rows.forEach(row -> byProduct.put(row.getProductId(), toResponse(row)));
        return byProduct;
    }

    /**
     * Saves the dairy facts sent with an admin product save. Only a dairy product can have them: sending them for any
     * other product is rejected rather than silently stored. A request without a dairy block leaves existing facts alone.
     */
    public void apply(Product product, DairyDetailsRequest request) {
        if (request == null) {
            return;
        }
        if (!catalogService.isDairy(product)) {
            throw new BusinessRuleException("Dairy details can only be set on a product in the Dairy category.");
        }
        ProductDairyDetails details = repository.findById(product.getId()).orElseGet(() -> {
            ProductDairyDetails created = new ProductDairyDetails();
            created.setProductId(product.getId());
            return created;
        });
        details.setUnit(request.unit());
        details.setPackSize(request.packSize());
        details.setShelfLifeDays(request.shelfLifeDays());
        details.setFreshDaily(request.freshDaily());
        String note = request.storageNote();
        details.setStorageNote(note == null || note.isBlank() ? null : note.trim());
        repository.save(details);
    }

    static DairyDetailsResponse toResponse(ProductDairyDetails row) {
        return new DairyDetailsResponse(row.getUnit(), row.getPackSize(), row.getShelfLifeDays(), row.isFreshDaily(), row.getStorageNote());
    }
}
