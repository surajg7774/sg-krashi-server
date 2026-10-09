package com.sgkrashi.dairy.repository;

import com.sgkrashi.dairy.entity.DairySubscription;
import com.sgkrashi.dairy.entity.SubscriptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DairySubscriptionRepository extends JpaRepository<DairySubscription, Long> {

    List<DairySubscription> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<DairySubscription> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DairySubscription s where s.id = :id and s.userId = :userId")
    Optional<DairySubscription> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DairySubscription s where s.id = :id")
    Optional<DairySubscription> findByIdForUpdate(@Param("id") Long id);

    /** Ids the nightly job considers, in a fixed order so that when stock is short the earliest subscriptions are served first. */
    @Query("select s.id from DairySubscription s where s.status <> :cancelled and s.startDate <= :date order by s.id asc")
    List<Long> findIdsToConsider(@Param("date") LocalDate date, @Param("cancelled") SubscriptionStatus cancelled);

    @Query("""
            select s from DairySubscription s
            where (:status is null or s.status = :status)
              and (:userId is null or s.userId = :userId)
              and (:productId is null or s.productId = :productId)
            order by s.createdAt desc
            """)
    Page<DairySubscription> search(
            @Param("status") SubscriptionStatus status,
            @Param("userId") Long userId,
            @Param("productId") Long productId,
            Pageable pageable);

    long countByProductIdAndStatusNot(Long productId, SubscriptionStatus status);
}
