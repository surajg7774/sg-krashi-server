package com.sgkrashi.order.entity;

/**
 * Who made an order status change, recorded on every {@link OrderStatusHistory}
 * row. {@code userId} is null for {@link Role#SYSTEM} (the payment webhook).
 */
public record StatusChangeActor(Role role, Long userId) {

    public enum Role {
        CUSTOMER,
        ADMIN,
        SYSTEM
    }

    public static StatusChangeActor customer(Long userId) {
        return new StatusChangeActor(Role.CUSTOMER, userId);
    }

    public static StatusChangeActor admin(Long userId) {
        return new StatusChangeActor(Role.ADMIN, userId);
    }

    public static StatusChangeActor system() {
        return new StatusChangeActor(Role.SYSTEM, null);
    }
}
