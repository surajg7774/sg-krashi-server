-- V5__product_catalog.sql
-- Module 5 catalog: categories, products, media assets, plus seed data (Admin
-- product management doesn't exist until Module 15, so this migration is the
-- only way to have real data to browse for now).

CREATE TABLE product_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT NULL,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(170) NOT NULL UNIQUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_product_categories_parent FOREIGN KEY (parent_id) REFERENCES product_categories (id)
);

CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    stock_qty INT NOT NULL DEFAULT 0,
    is_organic_certified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES product_categories (id)
);

CREATE INDEX idx_products_category_id ON products (category_id);
CREATE INDEX idx_products_is_active ON products (is_active);
CREATE FULLTEXT INDEX idx_products_name_fulltext ON products (name);

-- Polymorphic-lite: owner_id deliberately has no FK, since it can point at
-- products, and later crop listings/equipment/stays. See MediaAsset's Javadoc
-- for the tradeoff (no referential integrity, but no per-owner-type media table).
CREATE TABLE media_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_type VARCHAR(50) NOT NULL,
    owner_id BIGINT NOT NULL,
    url VARCHAR(500) NOT NULL,
    alt_text VARCHAR(255) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_media_assets_owner ON media_assets (owner_type, owner_id);

-- Seed: 5 top-level categories.
INSERT INTO product_categories (parent_id, name, slug, created_at, updated_at, is_active) VALUES
    (NULL, 'Grains', 'grains', NOW(6), NOW(6), TRUE),
    (NULL, 'Pulses & Lentils', 'pulses-lentils', NOW(6), NOW(6), TRUE),
    (NULL, 'Cold-Pressed Oils', 'cold-pressed-oils', NOW(6), NOW(6), TRUE),
    (NULL, 'Spices & Seasonings', 'spices-seasonings', NOW(6), NOW(6), TRUE),
    (NULL, 'Honey & Natural Sweeteners', 'honey-natural-sweeteners', NOW(6), NOW(6), TRUE);

-- Seed: 19 products across the 5 categories. Mixed is_organic_certified so the
-- organic-only filter is actually testable, and one zero-stock product
-- (organic-masoor-dal-1kg) so out-of-stock display is testable too.
INSERT INTO products (category_id, name, slug, description, price, stock_qty, is_organic_certified, created_at, updated_at, is_active) VALUES
    ((SELECT id FROM product_categories WHERE slug = 'grains'), 'Organic Basmati Rice (1kg)', 'organic-basmati-rice-1kg', 'Aged, long-grain basmati rice grown without synthetic pesticides. Aromatic and fluffy when cooked.', 249.00, 120, TRUE, NOW(6), NOW(6), TRUE),
    ((SELECT id FROM product_categories WHERE slug = 'grains'), 'Organic Brown Rice (1kg)', 'organic-brown-rice-1kg', 'Unpolished whole-grain brown rice, rich in fibre, grown on certified organic farms.', 189.00, 80, TRUE, NOW(6), NOW(6), TRUE),
    ((SELECT id FROM product_categories WHERE slug = 'grains'), 'Whole Wheat Atta (5kg)', 'whole-wheat-atta-5kg', 'Stone-ground whole wheat flour, milled fresh in small batches for everyday rotis and parathas.', 275.00, 200, FALSE, NOW(6), NOW(6), TRUE),
    ((SELECT id FROM product_categories WHERE slug = 'grains'), 'Organic Foxtail Millet (500g)', 'organic-foxtail-millet-500g', 'A gluten-free ancient grain, high in fibre and iron, grown using traditional organic methods.', 159.00, 60, TRUE, NOW(6), NOW(6), TRUE),

    ((SELECT id FROM product_categories WHERE slug = 'pulses-lentils'), 'Organic Toor Dal (1kg)', 'organic-toor-dal-1kg', 'Split pigeon peas grown organically, a staple for everyday dal and sambar.', 195.00, 100, TRUE, NOW(6), NOW(6), TRUE),
    ((SELECT id FROM product_categories WHERE slug = 'pulses-lentils'), 'Organic Moong Dal (1kg)', 'organic-moong-dal-1kg', 'Split, skinned green gram, easy to digest and quick to cook, grown without chemical inputs.', 210.00, 90, TRUE, NOW(6), NOW(6), TRUE),
    ((SELECT id FROM product_categories WHERE slug = 'pulses-lentils'), 'Chana Dal (1kg)', 'chana-dal-1kg', 'Split Bengal gram, a versatile everyday lentil for dals, snacks and sweets.', 145.00, 150, FALSE, NOW(6), NOW(6), TRUE),
    ((SELECT id FROM product_categories WHERE slug = 'pulses-lentils'), 'Organic Masoor Dal (1kg)', 'organic-masoor-dal-1kg', 'Split red lentils, organically grown, quick-cooking and mild in flavour.', 175.00, 0, TRUE, NOW(6), NOW(6), TRUE),

    ((SELECT id FROM product_categories WHERE slug = 'cold-pressed-oils'), 'Cold-Pressed Groundnut Oil (1L)', 'cold-pressed-groundnut-oil-1l', 'Traditionally extracted using a wooden churner (ghani), retaining natural aroma and nutrients.', 320.00, 45, TRUE, NOW(6), NOW(6), TRUE),
    ((SELECT id FROM product_categories WHERE slug = 'cold-pressed-oils'), 'Cold-Pressed Mustard Oil (1L)', 'cold-pressed-mustard-oil-1l', 'Pungent, unrefined mustard oil extracted at low temperatures to preserve flavour.', 280.00, 55, TRUE, NOW(6), NOW(6), TRUE),
    ((SELECT id FROM product_categories WHERE slug = 'cold-pressed-oils'), 'Cold-Pressed Coconut Oil (500ml)', 'cold-pressed-coconut-oil-500ml', 'Virgin coconut oil, cold-pressed from fresh coconut milk with no chemical processing.', 350.00, 70, TRUE, NOW(6), NOW(6), TRUE),
    ((SELECT id FROM product_categories WHERE slug = 'cold-pressed-oils'), 'Refined Sunflower Oil (1L)', 'refined-sunflower-oil-1l', 'A light, everyday cooking oil suited for high-heat frying and sauteing.', 165.00, 130, FALSE, NOW(6), NOW(6), TRUE),

    ((SELECT id FROM product_categories WHERE slug = 'spices-seasonings'), 'Organic Turmeric Powder (200g)', 'organic-turmeric-powder-200g', 'Sun-dried and stone-ground turmeric with high curcumin content, grown organically.', 95.00, 200, TRUE, NOW(6), NOW(6), TRUE),
    ((SELECT id FROM product_categories WHERE slug = 'spices-seasonings'), 'Organic Red Chilli Powder (200g)', 'organic-red-chilli-powder-200g', 'Sun-dried red chillies ground fresh, offering a natural deep colour and heat.', 110.00, 180, TRUE, NOW(6), NOW(6), TRUE),
    ((SELECT id FROM product_categories WHERE slug = 'spices-seasonings'), 'Black Pepper Whole (100g)', 'black-pepper-whole-100g', 'Bold, aromatic whole black peppercorns, sourced from small hill-farm growers.', 140.00, 90, FALSE, NOW(6), NOW(6), TRUE),
    ((SELECT id FROM product_categories WHERE slug = 'spices-seasonings'), 'Organic Garam Masala (100g)', 'organic-garam-masala-100g', 'A traditional blend of roasted, organically grown whole spices, ground fresh.', 130.00, 75, TRUE, NOW(6), NOW(6), TRUE),

    ((SELECT id FROM product_categories WHERE slug = 'honey-natural-sweeteners'), 'Raw Forest Honey (500g)', 'raw-forest-honey-500g', 'Unprocessed, unheated honey harvested from wild forest beehives.', 385.00, 40, TRUE, NOW(6), NOW(6), TRUE),
    ((SELECT id FROM product_categories WHERE slug = 'honey-natural-sweeteners'), 'Organic Jaggery Powder (1kg)', 'organic-jaggery-powder-1kg', 'Unrefined cane sugar alternative, made without chemical clarifiers.', 145.00, 110, TRUE, NOW(6), NOW(6), TRUE),
    ((SELECT id FROM product_categories WHERE slug = 'honey-natural-sweeteners'), 'Multi-Flora Honey (250g)', 'multi-flora-honey-250g', 'A blend of seasonal wildflower nectars, naturally raw and unfiltered.', 220.00, 65, FALSE, NOW(6), NOW(6), TRUE);

-- Seed: one placeholder image per product (stable placehold.co URLs, never broken links).
INSERT INTO media_assets (owner_type, owner_id, url, alt_text, sort_order, created_at, updated_at, is_active)
SELECT 'PRODUCT', id, CONCAT('https://placehold.co/800x800?text=', REPLACE(name, ' ', '+')), name, 0, NOW(6), NOW(6), TRUE
FROM products;

-- Seed: two extra angles for the Cold-Pressed Coconut Oil product, so the
-- image gallery has a real multi-image product to test against.
INSERT INTO media_assets (owner_type, owner_id, url, alt_text, sort_order, created_at, updated_at, is_active)
SELECT 'PRODUCT', id, 'https://placehold.co/800x800?text=Coconut+Oil+Angle+2', 'Cold-Pressed Coconut Oil, angle 2', 1, NOW(6), NOW(6), TRUE
FROM products WHERE slug = 'cold-pressed-coconut-oil-500ml';

INSERT INTO media_assets (owner_type, owner_id, url, alt_text, sort_order, created_at, updated_at, is_active)
SELECT 'PRODUCT', id, 'https://placehold.co/800x800?text=Coconut+Oil+Label', 'Cold-Pressed Coconut Oil, label detail', 2, NOW(6), NOW(6), TRUE
FROM products WHERE slug = 'cold-pressed-coconut-oil-500ml';
