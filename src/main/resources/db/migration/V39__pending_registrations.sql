-- V39__pending_registrations.sql
-- Replaces the stateless JWT-based "verify your email" link with a real,
-- server-side pending-registration row backing an OTP flow instead. A
-- stateless token can't support resend/rate-limit tracking across multiple
-- requests (there's nothing to count against), so this table persists
-- exactly what the old JWT's claims carried (name/email/password_hash/
-- phone) plus the OTP state itself. One row per email — a repeat
-- registration attempt for the same still-pending email overwrites the row
-- (fresh OTP, fresh data) rather than accumulating duplicates.

CREATE TABLE pending_registrations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    otp_hash VARCHAR(255) NOT NULL,
    otp_expires_at TIMESTAMP(6) NOT NULL,
    last_sent_at TIMESTAMP(6) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);
