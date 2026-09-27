-- V37__device_tokens.sql
-- Phase E: FCM push notifications. A device token identifies one specific
-- app install, not a user — the same user can be logged in on multiple
-- devices (multiple rows), and the same physical device's token can outlive
-- a logout/login as a different user, which is exactly why `token` itself
-- (not user_id+token) is the unique key: a re-registration from the same
-- device always updates the existing row's user_id/platform rather than
-- creating a duplicate.

CREATE TABLE device_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    platform VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_device_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_device_tokens_user_id ON device_tokens (user_id);
