package com.sgkrashi.review.repository;

import com.sgkrashi.review.entity.Review;
import com.sgkrashi.review.entity.ReviewTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(ReviewTargetType targetType, Long targetId, Pageable pageable);

    long countByTargetTypeAndTargetId(ReviewTargetType targetType, Long targetId);

    /**
     * MySQL unique indexes on a nullable column allow multiple NULLs — these
     * only ever constrain the non-null rows, which is exactly the intended
     * "at most one review per completed transaction" rule (see {@code
     * Review}'s Javadoc on why order_item_id/booking_id are the uniqueness
     * key, not user_id+target_id).
     */
    boolean existsByOrderItemId(Long orderItemId);

    boolean existsByBookingId(Long bookingId);

    @Query("select avg(r.rating) from Review r where r.targetType = :targetType and r.targetId = :targetId")
    Double averageRating(@Param("targetType") ReviewTargetType targetType, @Param("targetId") Long targetId);
}
