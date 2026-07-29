package com.sgkrashi.productstore.service.impl;

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

    public ProductCategoryServiceImpl(ProductCategoryRepository productCategoryRepository) {
        this.productCategoryRepository = productCategoryRepository;
    }

    @Override
    public List<ProductCategoryResponse> getCategoryTree() {
        List<ProductCategory> allCategories = productCategoryRepository.findByIsActiveTrue();

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
