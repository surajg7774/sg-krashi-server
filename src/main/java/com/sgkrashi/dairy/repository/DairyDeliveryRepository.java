package com.sgkrashi.dairy.repository;

import com.sgkrashi.dairy.entity.DairyDelivery;
import com.sgkrashi.dairy.entity.DairyDeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DairyDeliveryRepository extends JpaRepository<DairyDelivery, Long> {

    boolean existsBySubscriptionIdAndDeliveryDate(Long subscriptionId, LocalDate deliveryDate);

    Optional<DairyDelivery> findBySubscriptionIdAndDeliveryDate(Long subscriptionId, LocalDate deliveryDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DairyDelivery d where d.id = :id")
    Optional<DairyDelivery> findByIdForUpdate(@Param("id") Long id);

    Page<DairyDelivery> findBySubscriptionIdOrderByDeliveryDateDesc(Long subscriptionId, Pageable pageable);

    List<DairyDelivery> findBySubscriptionIdAndDeliveryDateIn(Long subscriptionId, Collection<LocalDate> dates);

    /** Prepared deliveries of one subscription that are still waiting and fall on or after {@code from}. */
    List<DairyDelivery> findBySubscriptionIdAndStatusAndDeliveryDateGreaterThanEqual(
            Long subscriptionId, DairyDeliveryStatus status, LocalDate from);
}
