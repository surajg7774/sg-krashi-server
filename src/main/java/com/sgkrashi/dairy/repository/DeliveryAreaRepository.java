package com.sgkrashi.dairy.repository;

import com.sgkrashi.dairy.entity.DeliveryArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryAreaRepository extends JpaRepository<DeliveryArea, Long> {

    long countByIsActiveTrue();

    boolean existsByIsActiveTrueAndPincode(String pincode);

    Optional<DeliveryArea> findByPincode(String pincode);

    List<DeliveryArea> findAllByOrderByPincodeAsc();
}
