-- V16__admin_management.sql
-- Module 16: Admin Order/Booking/Inquiry management + refunds.

-- Refund tracking — nullable, only ever populated once a Payment reaches
-- REFUNDED via RefundService's real Razorpay refund call.
ALTER TABLE payments
    ADD COLUMN refund_id VARCHAR(100) NULL,
    ADD COLUMN refunded_at TIMESTAMP(6) NULL;

-- Internal, Admin-only free text — never shown to the customer/inquirer.
-- Did not already exist on any of these three tables (checked V1-V13).
ALTER TABLE orders ADD COLUMN admin_notes TEXT NULL;
ALTER TABLE bookings ADD COLUMN admin_notes TEXT NULL;
ALTER TABLE inquiries ADD COLUMN admin_notes TEXT NULL;
