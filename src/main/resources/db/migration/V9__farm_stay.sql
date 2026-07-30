-- V9__farm_stay.sql
-- Module 9: Farm Stay listings, reusing Module 8's generic booking engine
-- with bookable_type = 'STAY'.

-- Location is embedded directly (not a FK to addresses) — addresses.user_id
-- is NOT NULL and every read/write there assumes a customer owner; a stay
-- listing has one fixed location and no owning customer. See StayListing.java
-- for the full reasoning.
CREATE TABLE stay_listings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    max_guests INT NOT NULL,
    nightly_rate DECIMAL(10, 2) NOT NULL,
    amenities TEXT NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255) NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    pincode VARCHAR(10) NOT NULL,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_stay_listings_is_active ON stay_listings (is_active);

-- Module 8's bookings table gains a nullable guest_count column — only
-- meaningful for STAY bookings, NULL for EQUIPMENT. Additive, backward
-- compatible with all existing Module 8 data (defaults to NULL).
ALTER TABLE bookings ADD COLUMN guest_count INT NULL AFTER total_price;

-- Seed: 7 farm stay listings across different regions, varied max_guests/rate.
INSERT INTO stay_listings (name, slug, description, max_guests, nightly_rate, amenities, address_line1, address_line2, city, state, pincode, is_available, created_at, updated_at, is_active) VALUES
    ('Sunrise Orchard Cottage', 'sunrise-orchard-cottage', 'A cosy cottage nestled in a working mango orchard, with a private veranda overlooking the trees.', 2, 2200.00, 'Wi-Fi,Home-cooked meals,Bonfire,Farm tour,Parking', 'Village Road, Near Orchard Gate', NULL, 'Nashik', 'Maharashtra', '422001', TRUE, NOW(6), NOW(6), TRUE),
    ('Riverside Farm House', 'riverside-farm-house', 'A spacious farm house on the banks of a seasonal river, ideal for families and small groups.', 6, 4500.00, 'Wi-Fi,Home-cooked meals,River access,Bonfire,Parking,Kitchen', 'Riverside Lane', 'Behind Grain Silo', 'Wardha', 'Maharashtra', '442001', TRUE, NOW(6), NOW(6), TRUE),
    ('Terraced Hills Homestay', 'terraced-hills-homestay', 'Simple rooms on a terraced hill farm, with sweeping valley views at sunrise.', 4, 3200.00, 'Wi-Fi,Home-cooked meals,Valley view,Trekking guide', 'Hillside Road', NULL, 'Wayanad', 'Kerala', '673121', TRUE, NOW(6), NOW(6), TRUE),
    ('Mustard Field Retreat', 'mustard-field-retreat', 'A quiet retreat surrounded by mustard fields, popular in the winter flowering season.', 3, 2800.00, 'Wi-Fi,Home-cooked meals,Bonfire,Bicycle rental', 'Sarson Farm Road', NULL, 'Bharatpur', 'Rajasthan', '321001', TRUE, NOW(6), NOW(6), TRUE),
    ('Coconut Grove Beach Farm', 'coconut-grove-beach-farm', 'A coastal coconut farm stay a short walk from the beach, with hammocks and open-air dining.', 5, 3800.00, 'Wi-Fi,Home-cooked meals,Beach access,Parking,Kitchen', 'Grove Lane', 'Off Coastal Highway', 'Udupi', 'Karnataka', '576101', TRUE, NOW(6), NOW(6), TRUE),
    ('Highland Tea Estate Cabin', 'highland-tea-estate-cabin', 'A small cabin on a working tea estate, cool climate year-round, guided estate walks available.', 2, 3500.00, 'Wi-Fi,Home-cooked meals,Estate tour,Bonfire', 'Estate Road 4', NULL, 'Munnar', 'Kerala', '685612', TRUE, NOW(6), NOW(6), TRUE),
    ('Limited Vineyard Guest Room', 'limited-vineyard-guest-room', 'A single guest room on a boutique vineyard, booked out most weekends — plan ahead.', 2, 5000.00, 'Wi-Fi,Home-cooked meals,Vineyard tour,Wine tasting', 'Vineyard Approach Road', NULL, 'Nashik', 'Maharashtra', '422003', TRUE, NOW(6), NOW(6), TRUE);

-- Seed: one placeholder image per stay listing.
INSERT INTO media_assets (owner_type, owner_id, url, alt_text, sort_order, created_at, updated_at, is_active)
SELECT 'STAY', id, CONCAT('https://placehold.co/800x800?text=', REPLACE(name, ' ', '+')), name, 0, NOW(6), NOW(6), TRUE
FROM stay_listings;

-- Seed: a lock anchor row for every stay listing (see Module 8's BookingLock
-- Javadoc — pre-seeding avoids the cold-start get-or-create race in practice
-- for this module's fixed, Admin-curated listing set).
INSERT INTO booking_locks (bookable_type, bookable_id)
SELECT 'STAY', id FROM stay_listings;

-- Seed: a few pre-existing CONFIRMED bookings so the calendar shows real
-- blocked dates from the start — same "no fabricated user" precedent as
-- Module 8: attached to whichever real user already exists at migration
-- time, inserting nothing on a genuinely fresh database.
INSERT INTO bookings (user_id, bookable_type, bookable_id, start_date, end_date, status, total_price, guest_count, created_at, updated_at, is_active)
SELECT (SELECT id FROM users ORDER BY id LIMIT 1), 'STAY', (SELECT id FROM stay_listings WHERE slug = 'riverside-farm-house'),
       '2026-08-15', '2026-08-18', 'CONFIRMED', 4500.00 * 3, 4, NOW(6), NOW(6), TRUE
WHERE EXISTS (SELECT 1 FROM users);

INSERT INTO bookings (user_id, bookable_type, bookable_id, start_date, end_date, status, total_price, guest_count, created_at, updated_at, is_active)
SELECT (SELECT id FROM users ORDER BY id LIMIT 1), 'STAY', (SELECT id FROM stay_listings WHERE slug = 'limited-vineyard-guest-room'),
       '2026-09-05', '2026-09-07', 'CONFIRMED', 5000.00 * 2, 2, NOW(6), NOW(6), TRUE
WHERE EXISTS (SELECT 1 FROM users);
