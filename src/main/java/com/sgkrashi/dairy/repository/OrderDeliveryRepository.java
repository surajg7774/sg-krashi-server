package com.sgkrashi.dairy.repository;

import com.sgkrashi.dairy.entity.OrderDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderDeliveryRepository extends JpaRepository<OrderDelivery, Long> {
}
