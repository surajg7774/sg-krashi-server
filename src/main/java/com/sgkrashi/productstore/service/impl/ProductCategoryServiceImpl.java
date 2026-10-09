package com.sgkrashi.productstore.service.impl;

import com.sgkrashi.dairy.service.DairyCatalogService;
import com.sgkrashi.productstore.dto.response.ProductCategoryResponse;
import com.sgkrashi.productstore.entity.ProductCategory;
import com.sgkrashi.productstore.repository.ProductCategoryRepository;
import com.sgkrashi.productstore.service.ProductCategoryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductCategoryServiceImpl implements ProductCategoryService {

    private final ProductCategoryRepository productCategoryRepository;
    private final DairyCatalogService dairyCatalogService;

    public ProductCategoryServiceImpl(ProductCategoryRepository productCategoryRepository, DairyCatalogService dairyCatalogService) {
        this.productCategoryRepository = productCategoryRepository;
        this.dairyCatalogService = dairyCatalogService;
    }

    @Override
    public List<ProductCategoryResponse> getCategoryTree() {
        List<ProductCategory> allCategories = productCategoryRepository.findByIsActiveTrue();

        // The Dairy category stays out of the public list until it has at least one active product, so a shop with no
        // dairy yet shows no empty "Dairy" filter (web or mobile).
        if (!dairyCatalogService.hasActiveDairyProducts()) {
            java.util.Set<Long> dairyIds = dairyCatalogService.dairyCategoryIds();
            allCategories = allCategories.stream().filter(category -> !dairyIds.contains(category.getId())).toList();
        }

        Map<Long, List<ProductCategory>> childrenByParentId = allCategories.stream()
                .filter(category -> category.getParent() != null)
                .collect(Collectors.groupingBy(category -> category.getParent().getId()));

        return allCategories.stream()
                .filter(category -> category.getParent() == null)
                .map(category -> toNode(category, childrenByParentId))
                .toList();
    }

    private ProductCategoryResponse toNode(ProductCategory category, Map<Long, List<ProductCategory>> childrenByParentId) {
        List<ProductCategoryResponse> children = childrenByParentId
                .getOrDefault(category.getId(), List.of()).stream()
                .map(child -> toNode(child, childrenByParentId))
                .toList();
        return new ProductCategoryResponse(category.getId(), category.getName(), category.getSlug(), children);
    }
}
