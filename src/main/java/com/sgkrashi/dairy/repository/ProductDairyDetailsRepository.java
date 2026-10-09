package com.sgkrashi.dairy.repository;

import com.sgkrashi.dairy.entity.ProductDairyDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProductDairyDetailsRepository extends JpaRepository<ProductDairyDetails, Long> {

    List<ProductDairyDetails> findByProductIdIn(Collection<Long> productIds);
}
