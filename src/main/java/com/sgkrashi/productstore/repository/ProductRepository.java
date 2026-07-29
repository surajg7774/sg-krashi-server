package com.sgkrashi.productstore.repository;

import com.sgkrashi.productstore.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // @EntityGraph eagerly joins category in the same query, rather than
    // leaving it lazy — avoids both a LazyInitializationException once the
    // session closes (mapping happens after the repository call returns) and
    // an N+1 query per row when the mapper reads category.getName().

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findByIdAndIsActiveTrue(Long id);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findBySlugAndIsActiveTrue(String slug);

    @EntityGraph(attributePaths = "category")
    List<Product> findTop6ByCategoryIdAndIsActiveTrueAndIdNot(Long categoryId, Long excludedId);
}
