-- V20__farmer_role.sql
-- Module 20: the FARMER role was never actually seeded. Module 7 reserved
-- crop_listings.farmer_id for this module and the architecture doc assumed
-- FARMER was seeded alongside CUSTOMER/ADMIN/SUPER_ADMIN in V2, but V2 only
-- seeded those three. The roles/user_roles tables and RoleRepository already
-- fully support an arbitrary role row — this is a one-line seed insert, not a
-- schema change.

INSERT INTO roles (name, created_at, updated_at, is_active) VALUES
    ('FARMER', NOW(6), NOW(6), TRUE);
