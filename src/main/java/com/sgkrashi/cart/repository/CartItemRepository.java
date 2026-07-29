package com.sgkrashi.cart.repository;

import com.sgkrashi.cart.entity.CartItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @EntityGraph(attributePaths = {"product", "product.category", "cropListing", "cropListing.category"})
    List<CartItem> findByCartId(Long cartId);

    /**
     * Same rows as {@link #findByCartId}, but WITHOUT eagerly loading the
     * {@code product}/{@code cropListing} associations. Checkout must use this
     * one: eagerly attaching those entities before the pessimistic-lock fetch
     * in {@code OrderServiceImpl.checkout()} would leave them already managed
     * in the persistence context, so the later locked query — despite
     * correctly locking the row at the DB level — would return the
     * already-attached (pre-lock, potentially stale) instance instead of
     * refreshing it from the now-current row. (This is the exact bug Module 6
     * found and fixed; Module 7's crop-listing path must follow the same rule.)
     */
    List<CartItem> findAllByCartId(Long cartId);

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    Optional<CartItem> findByCartIdAndCropListingId(Long cartId, Long cropListingId);

    void deleteByCartId(Long cartId);
}
