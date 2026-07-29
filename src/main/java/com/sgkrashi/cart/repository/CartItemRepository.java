package com.sgkrashi.cart.repository;

import com.sgkrashi.cart.entity.CartItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @EntityGraph(attributePaths = {"product", "product.category"})
    List<CartItem> findByCartId(Long cartId);

    /**
     * Same rows as {@link #findByCartId}, but WITHOUT eagerly loading the
     * {@code product} association. Checkout must use this one: eagerly
     * attaching Product entities before the pessimistic-lock fetch in
     * {@code OrderServiceImpl.checkout()} would leave those entities already
     * managed in the persistence context, so the later locked query — despite
     * correctly locking the row at the DB level — would return the
     * already-attached (pre-lock, potentially stale) instance instead of
     * refreshing it from the now-current row.
     */
    List<CartItem> findAllByCartId(Long cartId);

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    void deleteByCartId(Long cartId);
}
