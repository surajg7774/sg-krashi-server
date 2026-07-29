package com.sgkrashi.productstore.repository;

import com.sgkrashi.productstore.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    List<ProductCategory> findByIsActiveTrue();

    Optional<ProductCategory> findBySlugAndIsActiveTrue(String slug);
}
