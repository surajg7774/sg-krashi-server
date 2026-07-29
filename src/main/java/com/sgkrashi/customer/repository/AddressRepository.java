package com.sgkrashi.customer.repository;

import com.sgkrashi.customer.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdAndIsActiveTrueOrderByIsDefaultDescCreatedAtAsc(Long userId);
}
