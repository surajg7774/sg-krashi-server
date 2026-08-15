-- V25__crop_listing_organic_certified.sql
-- Adds the same organic-certified flag Product Store has had since
-- V5__product_catalog.sql, so the Crop Marketplace can filter on it too.
-- DEFAULT FALSE backfills every existing seeded listing; a handful of
-- pulses/vegetables are flipped TRUE below purely so the new filter has
-- something real to demonstrate against, not because of any specific
-- certification claim about the seed data.

ALTER TABLE crop_listings
    ADD COLUMN is_organic_certified BOOLEAN NOT NULL DEFAULT FALSE AFTER unit_price;

UPDATE crop_listings SET is_organic_certified = TRUE
WHERE slug IN ('toor-dal-split-pigeon-pea-maharashtra', 'moong-green-gram-rajasthan', 'fresh-tomatoes-karnataka');
