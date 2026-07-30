-- V10__inquiry_extensions.sql
-- Formalizes Module 3's minimal inquiries table for per-module inquiry types
-- and a full status workflow (Module 10). Alters the existing table only —
-- no new tables. New columns are nullable so Module 3's existing GENERAL
-- rows (which never had preferred_date/group_size) remain valid as-is.

ALTER TABLE inquiries
    ADD COLUMN preferred_date DATE NULL AFTER message,
    ADD COLUMN group_size INT NULL AFTER preferred_date;

-- idx_inquiries_module_type and idx_inquiries_status already exist from V3.
-- user_id had no index yet — needed for the new GET /api/v1/inquiries/my
-- lookup (findByUserIdOrderByCreatedAtDesc).
CREATE INDEX idx_inquiries_user_id ON inquiries (user_id);
