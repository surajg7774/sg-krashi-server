-- V12__reviews.sql
-- Module 12: a single generic reviews table (target_type/target_id, no FK on
-- target_id — same polymorphic-lite tradeoff as media_assets.owner_id, since
-- it points at products/crop_listings/equipment/stay_listings depending on
-- target_type) plus denormalized avg_rating/review_count on all four target
-- tables, recomputed on every new review rather than aggregated on read.

CREATE TABLE reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id BIGINT NOT NULL,
    order_item_id BIGINT NULL,
    booking_id BIGINT NULL,
    rating INT NOT NULL,
    comment TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE INDEX idx_reviews_target ON reviews (target_type, target_id);
CREATE INDEX idx_reviews_user_id ON reviews (user_id);

-- One review per completed transaction (not per user+target): a customer who
-- orders/books the same item twice on separate occasions may review each
-- experience independently. MySQL unique indexes allow multiple NULLs, so
-- these only constrain the non-null side (order_item_id for Product/CropListing
-- reviews, booking_id for Equipment/Stay reviews — always exactly one populated).
CREATE UNIQUE INDEX uq_reviews_order_item ON reviews (order_item_id);
CREATE UNIQUE INDEX uq_reviews_booking ON reviews (booking_id);

ALTER TABLE products
    ADD COLUMN avg_rating DECIMAL(3,2) NULL,
    ADD COLUMN review_count INT NOT NULL DEFAULT 0;

ALTER TABLE crop_listings
    ADD COLUMN avg_rating DECIMAL(3,2) NULL,
    ADD COLUMN review_count INT NOT NULL DEFAULT 0;

ALTER TABLE equipment
    ADD COLUMN avg_rating DECIMAL(3,2) NULL,
    ADD COLUMN review_count INT NOT NULL DEFAULT 0;

ALTER TABLE stay_listings
    ADD COLUMN avg_rating DECIMAL(3,2) NULL,
    ADD COLUMN review_count INT NOT NULL DEFAULT 0;
