-- V7__crop_marketplace.sql
-- Module 7: crop marketplace, and generalizing cart_items/order_items to
-- reference either a product or a crop listing.

CREATE TABLE crop_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(170) NOT NULL UNIQUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- farmer_id is reserved (nullable FK to users) for Module 20's Farmer Portal —
-- every listing here is Admin-curated seed data with farmer_id = NULL.
CREATE TABLE crop_listings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    farmer_id BIGINT NULL,
    crop_category_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    quantity_available INT NOT NULL DEFAULT 0,
    unit_price DECIMAL(10, 2) NOT NULL,
    harvest_date DATE NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_crop_listings_category FOREIGN KEY (crop_category_id) REFERENCES crop_categories (id),
    CONSTRAINT fk_crop_listings_farmer FOREIGN KEY (farmer_id) REFERENCES users (id)
);

CREATE INDEX idx_crop_listings_category_id ON crop_listings (crop_category_id);
CREATE INDEX idx_crop_listings_is_active ON crop_listings (is_active);

-- === Generalize cart_items to reference either a product or a crop listing ===
-- item_type gets a NOT NULL DEFAULT so this single ALTER backfills every
-- existing (pre-Module-7, product-only) row to 'PRODUCT' with no separate
-- UPDATE needed; the default is then dropped since the application always
-- sets item_type explicitly from here on.
ALTER TABLE cart_items
    ADD COLUMN item_type VARCHAR(20) NOT NULL DEFAULT 'PRODUCT' AFTER cart_id,
    ADD COLUMN crop_listing_id BIGINT NULL AFTER product_id,
    MODIFY COLUMN product_id BIGINT NULL;

ALTER TABLE cart_items ALTER COLUMN item_type DROP DEFAULT;

ALTER TABLE cart_items
    ADD CONSTRAINT fk_cart_items_crop_listing FOREIGN KEY (crop_listing_id) REFERENCES crop_listings (id),
    ADD CONSTRAINT uq_cart_items_cart_croplisting UNIQUE (cart_id, crop_listing_id);

-- Exactly one of product_id/crop_listing_id populated, matching item_type —
-- enforced in the service layer already; this is defense-in-depth, same
-- reasoning as the gateway_payment_id unique constraint from Module 6.
ALTER TABLE cart_items
    ADD CONSTRAINT chk_cart_items_item_ref CHECK (
        (item_type = 'PRODUCT' AND product_id IS NOT NULL AND crop_listing_id IS NULL) OR
        (item_type = 'CROP_LISTING' AND crop_listing_id IS NOT NULL AND product_id IS NULL)
    );

-- === Same generalization for order_items ===
ALTER TABLE order_items
    ADD COLUMN item_type VARCHAR(20) NOT NULL DEFAULT 'PRODUCT' AFTER order_id,
    ADD COLUMN crop_listing_id BIGINT NULL AFTER product_id,
    MODIFY COLUMN product_id BIGINT NULL;

ALTER TABLE order_items ALTER COLUMN item_type DROP DEFAULT;

ALTER TABLE order_items
    ADD CONSTRAINT fk_order_items_crop_listing FOREIGN KEY (crop_listing_id) REFERENCES crop_listings (id);

ALTER TABLE order_items
    ADD CONSTRAINT chk_order_items_item_ref CHECK (
        (item_type = 'PRODUCT' AND product_id IS NOT NULL AND crop_listing_id IS NULL) OR
        (item_type = 'CROP_LISTING' AND crop_listing_id IS NOT NULL AND product_id IS NULL)
    );

-- Seed: 3 crop categories.
INSERT INTO crop_categories (name, slug, created_at, updated_at, is_active) VALUES
    ('Grains', 'grains', NOW(6), NOW(6), TRUE),
    ('Pulses', 'pulses', NOW(6), NOW(6), TRUE),
    ('Vegetables', 'vegetables', NOW(6), NOW(6), TRUE);

-- Seed: 13 crop listings, mixed harvest dates (past and future), mixed prices,
-- farmer_id NULL throughout, one with quantity_available = 1 for the
-- concurrency test.
INSERT INTO crop_listings (crop_category_id, name, slug, description, quantity_available, unit_price, harvest_date, created_at, updated_at, is_active) VALUES
    ((SELECT id FROM crop_categories WHERE slug = 'grains'), 'Winter Wheat Batch - Punjab', 'winter-wheat-batch-punjab', 'Freshly harvested winter wheat from the Punjab plains, sold in bulk 50kg sacks.', 200, 22.50, '2026-03-15', NOW(6), NOW(6), TRUE),
    ((SELECT id FROM crop_categories WHERE slug = 'grains'), 'Basmati Paddy - Haryana', 'basmati-paddy-haryana', 'Long-grain basmati paddy, freshly threshed and sun-dried.', 150, 35.00, '2026-01-20', NOW(6), NOW(6), TRUE),
    ((SELECT id FROM crop_categories WHERE slug = 'grains'), 'Maize Batch - Bihar', 'maize-batch-bihar', 'Hybrid maize, high yield, sold by the quintal.', 300, 18.00, '2025-11-05', NOW(6), NOW(6), TRUE),
    ((SELECT id FROM crop_categories WHERE slug = 'grains'), 'Bajra (Pearl Millet) - Rajasthan', 'bajra-pearl-millet-rajasthan', 'Drought-resistant pearl millet, staple grain for arid regions.', 120, 24.00, '2026-09-10', NOW(6), NOW(6), TRUE),

    ((SELECT id FROM crop_categories WHERE slug = 'pulses'), 'Toor Dal (Split Pigeon Pea) - Maharashtra', 'toor-dal-split-pigeon-pea-maharashtra', 'Freshly harvested pigeon pea, unprocessed, sold in bulk.', 90, 68.00, '2026-02-01', NOW(6), NOW(6), TRUE),
    ((SELECT id FROM crop_categories WHERE slug = 'pulses'), 'Chana (Chickpea) Batch - Madhya Pradesh', 'chana-chickpea-batch-madhya-pradesh', 'Bold Kabuli chana, freshly harvested this season.', 110, 55.00, '2026-03-01', NOW(6), NOW(6), TRUE),
    ((SELECT id FROM crop_categories WHERE slug = 'pulses'), 'Moong (Green Gram) - Rajasthan', 'moong-green-gram-rajasthan', 'Whole green gram, high protein content, sold in 40kg bags.', 75, 82.00, '2025-10-15', NOW(6), NOW(6), TRUE),
    ((SELECT id FROM crop_categories WHERE slug = 'pulses'), 'Masoor (Red Lentil) - Uttar Pradesh', 'masoor-red-lentil-uttar-pradesh', 'Split red lentils, quick-cooking, sold in bulk sacks.', 60, 60.00, '2026-04-05', NOW(6), NOW(6), TRUE),

    ((SELECT id FROM crop_categories WHERE slug = 'vegetables'), 'Fresh Tomatoes - Karnataka', 'fresh-tomatoes-karnataka', 'Vine-ripened tomatoes, harvested this week, sold in 25kg crates.', 40, 15.00, '2026-01-28', NOW(6), NOW(6), TRUE),
    ((SELECT id FROM crop_categories WHERE slug = 'vegetables'), 'Onion Batch - Nashik', 'onion-batch-nashik', 'Premium red onions from Nashik, cured and ready for bulk sale.', 250, 20.00, '2025-12-20', NOW(6), NOW(6), TRUE),
    ((SELECT id FROM crop_categories WHERE slug = 'vegetables'), 'Potato Batch - Uttar Pradesh', 'potato-batch-uttar-pradesh', 'Table potatoes, cold-stored, sold in 50kg bags.', 180, 12.00, '2025-12-01', NOW(6), NOW(6), TRUE),
    ((SELECT id FROM crop_categories WHERE slug = 'vegetables'), 'Green Chilli Batch - Andhra Pradesh', 'green-chilli-batch-andhra-pradesh', 'Fresh green chillies, harvested and packed same day.', 30, 45.00, '2026-02-14', NOW(6), NOW(6), TRUE),
    ((SELECT id FROM crop_categories WHERE slug = 'vegetables'), 'Limited Cauliflower Batch - Himachal Pradesh', 'limited-cauliflower-batch-himachal-pradesh', 'Small late-season cauliflower batch, only one crate left.', 1, 30.00, '2026-01-10', NOW(6), NOW(6), TRUE);

-- Seed: one placeholder image per crop listing.
INSERT INTO media_assets (owner_type, owner_id, url, alt_text, sort_order, created_at, updated_at, is_active)
SELECT 'CROP_LISTING', id, CONCAT('https://placehold.co/800x800?text=', REPLACE(name, ' ', '+')), name, 0, NOW(6), NOW(6), TRUE
FROM crop_listings;
