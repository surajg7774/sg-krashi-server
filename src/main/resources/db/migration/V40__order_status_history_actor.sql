-- V40__order_status_history_actor.sql
-- Item 3 (order tracking): record WHO made each order status change.
--
-- Both columns are nullable and have no foreign key, on purpose:
--  * rows written before this migration stay NULL ("unknown") — nothing is
--    guessed or back-filled, because the old rows never recorded an actor;
--  * changed_by_user_id is a plain id (like audit_logs), so it can never block
--    a user's account deletion, which anonymizes the users row in place;
--  * changed_by_role is CUSTOMER (checkout), ADMIN (an administrator) or
--    SYSTEM (the payment webhook, no human involved).
-- Older server code / older mobile apps simply never see these columns.

ALTER TABLE order_status_history
    ADD COLUMN changed_by_role VARCHAR(20) NULL,
    ADD COLUMN changed_by_user_id BIGINT NULL;
