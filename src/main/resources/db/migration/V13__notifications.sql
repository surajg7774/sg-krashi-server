-- V13__notifications.sql
-- Module 13: a single generic notifications table (related_type/related_id,
-- no FK — same polymorphic-lite tradeoff as media_assets/reviews, since it
-- points at orders/bookings/inquiries depending on related_type).

CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    related_type VARCHAR(30) NULL,
    related_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Composite, in this column order: the unread-count badge's hot query is
-- always "this user's unread notifications", not a plain per-user scan.
CREATE INDEX idx_notifications_user_read ON notifications (user_id, is_read);
