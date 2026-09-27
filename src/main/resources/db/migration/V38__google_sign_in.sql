-- V38__google_sign_in.sql
-- A Google-only account has no password at all — password_hash must become
-- nullable to represent that honestly (a placeholder unusable hash was
-- considered and rejected: it would let future code silently assume every
-- account has a real password). google_id is Google's stable, never-reused
-- "sub" claim — the real identity key, not email (which can technically
-- change on Google's side, however rarely). UNIQUE with NULL allowed: MySQL
-- permits multiple NULLs in a UNIQUE column, which is exactly right here —
-- every existing password-only account has no google_id yet.

ALTER TABLE users MODIFY COLUMN password_hash VARCHAR(255) NULL;
ALTER TABLE users ADD COLUMN google_id VARCHAR(255) NULL UNIQUE;
