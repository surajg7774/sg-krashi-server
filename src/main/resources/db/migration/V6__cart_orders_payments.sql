-- V6__cart_orders_payments.sql
-- Module 6: cart, checkout/orders, and gateway-agnostic payments.

CREATE TABLE carts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_carts_user UNIQUE (user_id)
);

-- Hard-deleted (no is_active/timestamps) — see CartItem's Javadoc for why:
-- a soft-deleted row would otherwise collide with this unique constraint the
-- next time the same product is added back to the same cart.
CREATE TABLE cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id),
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uq_cart_items_cart_product UNIQUE (cart_id, product_id)
);

CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    order_number VARCHAR(40) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    total_amount DECIMAL(12, 2) NOT NULL,
    shipping_line1 VARCHAR(255) NOT NULL,
    shipping_line2 VARCHAR(255) NULL,
    shipping_city VARCHAR(100) NOT NULL,
    shipping_state VARCHAR(100) NOT NULL,
    shipping_pincode VARCHAR(10) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_orders_user_id ON orders (user_id);

-- product_name_snapshot / unit_price_snapshot are captured at checkout and
-- never recomputed from the live product row afterward.
CREATE TABLE order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name_snapshot VARCHAR(200) NOT NULL,
    unit_price_snapshot DECIMAL(10, 2) NOT NULL,
    quantity INT NOT NULL,
    line_total DECIMAL(12, 2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);

CREATE TABLE order_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    note VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE INDEX idx_order_status_history_order_id ON order_status_history (order_id);

-- payable_type/payable_id deliberately have no FK (same polymorphic-lite
-- tradeoff as media_assets.owner_id) so future modules (bookings) can reuse
-- this table for their own payments without a schema change.
CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payable_type VARCHAR(30) NOT NULL,
    payable_id BIGINT NOT NULL,
    gateway_order_id VARCHAR(100) NOT NULL UNIQUE,
    gateway_payment_id VARCHAR(100) NULL UNIQUE,
    amount DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_payments_payable ON payments (payable_type, payable_id);
