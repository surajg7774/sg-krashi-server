-- V8__booking_engine_equipment.sql
-- Module 8: generic booking engine (bookings, booking_locks) + equipment rental.

CREATE TABLE equipment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL UNIQUE,
    category VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    daily_rate DECIMAL(10, 2) NOT NULL,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_equipment_category ON equipment (category);
CREATE INDEX idx_equipment_is_active ON equipment (is_active);

-- Generic date-range reservation — bookable_type/bookable_id is polymorphic-lite
-- (no FK), same tradeoff as media_assets.owner_id and payments.payable_id, so
-- Module 9's StayListing can reuse this table without a schema change.
-- start_date is inclusive, end_date is EXCLUSIVE (checkout/return day) — see
-- Booking.java's Javadoc.
CREATE TABLE bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    bookable_type VARCHAR(20) NOT NULL,
    bookable_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_price DECIMAL(12, 2) NOT NULL,
    cancelled_at TIMESTAMP(6) NULL,
    cancellation_reason VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Composite index is the critical one — every overlap query filters by
-- exactly this pair before comparing date ranges.
CREATE INDEX idx_bookings_bookable ON bookings (bookable_type, bookable_id);
CREATE INDEX idx_bookings_user_id ON bookings (user_id);
CREATE INDEX idx_bookings_status ON bookings (status);

-- Lock anchor rows — one per (bookable_type, bookable_id), locked via
-- SELECT ... FOR UPDATE to serialize concurrent booking attempts against the
-- same bookable item. See BookingLock.java and BookingServiceImpl's Javadoc
-- for why the engine locks this instead of the bookable resource's own row.
CREATE TABLE booking_locks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bookable_type VARCHAR(20) NOT NULL,
    bookable_id BIGINT NOT NULL,
    CONSTRAINT uq_booking_locks_bookable UNIQUE (bookable_type, bookable_id)
);

-- Seed: 10 equipment items across 4 categories, realistic INR daily rates.
INSERT INTO equipment (name, slug, category, description, daily_rate, is_available, created_at, updated_at, is_active) VALUES
    ('Mahindra 575 DI Tractor (45 HP)', 'mahindra-575-di-tractor-45hp', 'Tractors', 'A reliable mid-size tractor suited for ploughing, tilling and haulage on small-to-medium farms.', 2500.00, TRUE, NOW(6), NOW(6), TRUE),
    ('Swaraj 744 FE Tractor (48 HP)', 'swaraj-744-fe-tractor-48hp', 'Tractors', 'Fuel-efficient tractor with strong haulage capacity, well suited for rotavator and trailer work.', 2800.00, TRUE, NOW(6), NOW(6), TRUE),
    ('Self-Propelled Combine Harvester', 'self-propelled-combine-harvester', 'Harvesters', 'High-capacity combine harvester for wheat, paddy and other cereal crops.', 8000.00, TRUE, NOW(6), NOW(6), TRUE),
    ('Multi-Crop Thresher Machine', 'multi-crop-thresher-machine', 'Harvesters', 'Diesel-powered thresher suitable for wheat, paddy, soybean and pulses.', 1500.00, TRUE, NOW(6), NOW(6), TRUE),
    ('Rotavator (6 ft, Tractor-Mounted)', 'rotavator-6ft-tractor-mounted', 'Tillage Equipment', 'Heavy-duty rotavator for fine seedbed preparation, tractor-mounted.', 1200.00, TRUE, NOW(6), NOW(6), TRUE),
    ('Disc Plough (3-Disc)', 'disc-plough-3-disc', 'Tillage Equipment', 'Rugged disc plough for primary tillage in heavier soils.', 700.00, TRUE, NOW(6), NOW(6), TRUE),
    ('Power Tiller (12 HP)', 'power-tiller-12hp', 'Tillage Equipment', 'Compact power tiller ideal for small plots and terraced fields.', 900.00, TRUE, NOW(6), NOW(6), TRUE),
    ('Tractor-Mounted Seed Drill', 'tractor-mounted-seed-drill', 'Sowing Equipment', 'Multi-row seed drill for precise, even seed placement and reduced seed wastage.', 1000.00, TRUE, NOW(6), NOW(6), TRUE),
    ('Sprinkler Irrigation Set (1 acre)', 'sprinkler-irrigation-set-1-acre', 'Irrigation Equipment', 'Portable sprinkler set covering roughly one acre per setup, easy to relocate.', 600.00, TRUE, NOW(6), NOW(6), TRUE),
    ('Drip Irrigation Kit (1 acre)', 'drip-irrigation-kit-1-acre', 'Irrigation Equipment', 'Water-efficient drip irrigation kit with tubing and emitters for one acre.', 500.00, TRUE, NOW(6), NOW(6), TRUE);

-- Seed: one placeholder image per equipment item.
INSERT INTO media_assets (owner_type, owner_id, url, alt_text, sort_order, created_at, updated_at, is_active)
SELECT 'EQUIPMENT', id, CONCAT('https://placehold.co/800x800?text=', REPLACE(name, ' ', '+')), name, 0, NOW(6), NOW(6), TRUE
FROM equipment;

-- Seed: a lock anchor row for every equipment item, so the cold-start
-- get-or-create race in BookingServiceImpl.acquireBookingLock is never
-- actually exercised for this module's fixed, Admin-curated equipment set —
-- see that method's Javadoc.
INSERT INTO booking_locks (bookable_type, bookable_id)
SELECT 'EQUIPMENT', id FROM equipment;

-- Seed: a few pre-existing CONFIRMED bookings so availability-blocking is
-- visibly testable from the start, WITHOUT fabricating a user account —
-- V2's migration already established "no seeded credentials, ever," so these
-- are attached to whichever real (registered-via-the-app) user happens to
-- already exist at migration time. On a genuinely fresh database with zero
-- users, these INSERT...SELECT statements simply insert nothing (no FK
-- violation, no fabricated login) until a real user has registered.
INSERT INTO bookings (user_id, bookable_type, bookable_id, start_date, end_date, status, total_price, created_at, updated_at, is_active)
SELECT (SELECT id FROM users ORDER BY id LIMIT 1), 'EQUIPMENT', (SELECT id FROM equipment WHERE slug = 'mahindra-575-di-tractor-45hp'),
       '2026-08-10', '2026-08-13', 'CONFIRMED', 2500.00 * 3, NOW(6), NOW(6), TRUE
WHERE EXISTS (SELECT 1 FROM users);

INSERT INTO bookings (user_id, bookable_type, bookable_id, start_date, end_date, status, total_price, created_at, updated_at, is_active)
SELECT (SELECT id FROM users ORDER BY id LIMIT 1), 'EQUIPMENT', (SELECT id FROM equipment WHERE slug = 'mahindra-575-di-tractor-45hp'),
       '2026-08-20', '2026-08-22', 'CONFIRMED', 2500.00 * 2, NOW(6), NOW(6), TRUE
WHERE EXISTS (SELECT 1 FROM users);

INSERT INTO bookings (user_id, bookable_type, bookable_id, start_date, end_date, status, total_price, created_at, updated_at, is_active)
SELECT (SELECT id FROM users ORDER BY id LIMIT 1), 'EQUIPMENT', (SELECT id FROM equipment WHERE slug = 'self-propelled-combine-harvester'),
       '2026-09-01', '2026-09-04', 'CONFIRMED', 8000.00 * 3, NOW(6), NOW(6), TRUE
WHERE EXISTS (SELECT 1 FROM users);
