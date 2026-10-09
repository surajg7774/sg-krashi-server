package com.sgkrashi.dairy.service;

import com.sgkrashi.productstore.entity.Product;
import com.sgkrashi.productstore.entity.ProductCategory;
import com.sgkrashi.productstore.repository.ProductCategoryRepository;
import com.sgkrashi.productstore.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Knows which products are dairy: every product filed under the "dairy" category or any category below it. That one
 * rule drives the Dairy page, the pincode/slot checks at checkout and the subscription eligibility check.
 */
@Service
public class DairyCatalogService {

    public static final String DAIRY_CATEGORY_SLUG = "dairy";

    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public DairyCatalogService(ProductCategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    /** Ids of the dairy category and its sub-categories; empty when there is no dairy category. */
    public Set<Long> dairyCategoryIds() {
        List<ProductCategory> all = categoryRepository.findAll();
        return descendantsOfDairy(all);
    }

    static Set<Long> descendantsOfDairy(List<ProductCategory> all) {
        Set<Long> ids = new HashSet<>();
        for (ProductCategory category : all) {
            if (DAIRY_CATEGORY_SLUG.equals(category.getSlug())) {
                ids.add(category.getId());
            }
        }
        boolean grew = !ids.isEmpty();
        while (grew) {
            grew = false;
            for (ProductCategory category : all) {
                ProductCategory parent = category.getParent();
                if (parent != null && ids.contains(parent.getId()) && ids.add(category.getId())) {
                    grew = true;
                }
            }
        }
        return ids;
    }

    public boolean isDairy(Product product, Set<Long> dairyCategoryIds) {
        return product.getCategory() != null && dairyCategoryIds.contains(product.getCategory().getId());
    }

    public boolean isDairy(Product product) {
        return isDairy(product, dairyCategoryIds());
    }

    /** True when at least one active product is filed under dairy (used to hide an empty Dairy category). */
    public boolean hasActiveDairyProducts() {
        Set<Long> ids = dairyCategoryIds();
        return !ids.isEmpty() && productRepository.countByCategoryIdInAndIsActiveTrue(ids) > 0;
    }
}
