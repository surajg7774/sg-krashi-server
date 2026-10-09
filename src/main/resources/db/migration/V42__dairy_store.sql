-- V42__dairy_store.sql
-- Dairy in the Store: dairy product details, admin-managed delivery areas and slots, the delivery choice saved on an
-- order, and subscriptions. Additive only: no existing table is altered, nothing is dropped, and no product, price,
-- pincode, timing or phone number is invented here (schema only, plus the one "Dairy" category the catalog needs
-- before an admin can file a dairy product under it - there is no admin screen for creating categories).

INSERT INTO product_categories (parent_id, name, slug, created_at, updated_at, is_active)
SELECT NULL, 'Dairy', 'dairy', NOW(6), NOW(6), TRUE FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM product_categories WHERE slug = 'dairy');

-- Extra facts about a dairy product. One row per dairy product; absent for every other product.
CREATE TABLE product_dairy_details (
    product_id BIGINT NOT NULL PRIMARY KEY,
    unit VARCHAR(20) NOT NULL,
    pack_size DECIMAL(10, 3) NOT NULL,
    shelf_life_days INT NULL,
    fresh_daily BOOLEAN NOT NULL DEFAULT FALSE,
    storage_note VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_product_dairy_details_product FOREIGN KEY (product_id) REFERENCES products (id)
);

-- Pincodes the farm delivers dairy to. No rows = no restriction.
CREATE TABLE delivery_areas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pincode VARCHAR(10) NOT NULL,
    label VARCHAR(100) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_delivery_areas_pincode UNIQUE (pincode)
);

-- Delivery windows. days_of_week is a comma list of MON,TUE,WED,THU,FRI,SAT,SUN. No rows = nothing to choose.
CREATE TABLE delivery_slots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    days_of_week VARCHAR(40) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- The slot and date a customer chose for an order that contains dairy. Only dairy orders have a row; the slot's name
-- and times are copied so a later edit to the slot cannot rewrite history.
CREATE TABLE order_delivery (
    order_id BIGINT NOT NULL PRIMARY KEY,
    slot_id BIGINT NULL,
    slot_name VARCHAR(100) NULL,
    slot_start TIME NULL,
    slot_end TIME NULL,
    delivery_date DATE NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_order_delivery_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE TABLE dairy_subscriptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    frequency VARCHAR(10) NOT NULL,
    weekdays VARCHAR(40) NULL,
    start_date DATE NOT NULL,
    slot_id BIGINT NULL,
    status VARCHAR(12) NOT NULL,
    pause_from DATE NULL,
    pause_to DATE NULL,
    cancelled_at TIMESTAMP(6) NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255) NULL,
    address_city VARCHAR(100) NOT NULL,
    address_state VARCHAR(100) NOT NULL,
    address_pincode VARCHAR(10) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_dairy_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_dairy_subscriptions_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_dairy_subscriptions_slot FOREIGN KEY (slot_id) REFERENCES delivery_slots (id)
);

CREATE INDEX idx_dairy_subscriptions_user ON dairy_subscriptions (user_id);
CREATE INDEX idx_dairy_subscriptions_status ON dairy_subscriptions (status);

CREATE TABLE dairy_subscription_skips (
    subscription_id BIGINT NOT NULL,
    skip_date DATE NOT NULL,
    PRIMARY KEY (subscription_id, skip_date),
    CONSTRAINT fk_dairy_skips_subscription FOREIGN KEY (subscription_id) REFERENCES dairy_subscriptions (id)
);

-- One row per subscription per delivery date, written by the nightly job. The unique key is what makes the job safe to
-- run twice. Name, price, slot and address are copied: they are what was promised for that day.
CREATE TABLE dairy_deliveries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id BIGINT NOT NULL,
    delivery_date DATE NOT NULL,
    quantity INT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    line_total DECIMAL(12, 2) NOT NULL,
    slot_name VARCHAR(100) NULL,
    slot_start TIME NULL,
    slot_end TIME NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255) NULL,
    address_city VARCHAR(100) NOT NULL,
    address_state VARCHAR(100) NOT NULL,
    address_pincode VARCHAR(10) NOT NULL,
    status VARCHAR(30) NOT NULL,
    stock_reserved BOOLEAN NOT NULL DEFAULT FALSE,
    cash_collected BOOLEAN NOT NULL DEFAULT FALSE,
    note VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_dairy_deliveries_subscription FOREIGN KEY (subscription_id) REFERENCES dairy_subscriptions (id),
    CONSTRAINT uq_dairy_deliveries_subscription_date UNIQUE (subscription_id, delivery_date)
);

CREATE INDEX idx_dairy_deliveries_date_status ON dairy_deliveries (delivery_date, status);
