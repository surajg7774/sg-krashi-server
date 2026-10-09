package com.sgkrashi.dairy.repository;

import com.sgkrashi.dairy.entity.DeliverySlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliverySlotRepository extends JpaRepository<DeliverySlot, Long> {

    List<DeliverySlot> findByIsActiveTrueOrderBySortOrderAscStartTimeAsc();

    Optional<DeliverySlot> findByIdAndIsActiveTrue(Long id);

    List<DeliverySlot> findAllByOrderBySortOrderAscStartTimeAsc();
}
