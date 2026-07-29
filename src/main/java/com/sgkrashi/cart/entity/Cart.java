package com.sgkrashi.cart.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A user's single active cart — one row per user, created lazily on the
 * first item add (see {@code CartServiceImpl.getOrCreateCart}).
 */
@Entity
@Table(name = "carts")
public class Cart extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
