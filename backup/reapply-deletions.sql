-- Re-applies ONE account deletion to a database restored from a backup taken
-- before the account was deleted. Mirrors AccountErasureRepository.erase() in
-- the server code - keep them identical (a unit test fails if the tables drift).
-- Run through reapply-deletions.sh, which sets @uid and @orig_email first and
-- wraps this file in one transaction.

DELETE FROM refresh_tokens WHERE user_id = @uid;
DELETE FROM device_tokens WHERE user_id = @uid;
DELETE FROM addresses WHERE user_id = @uid;
DELETE ci FROM cart_items ci JOIN carts c ON ci.cart_id = c.id WHERE c.user_id = @uid;
DELETE FROM carts WHERE user_id = @uid;
DELETE FROM notifications WHERE user_id = @uid;
DELETE cm FROM chat_messages cm JOIN chat_sessions cs ON cm.session_id = cs.id WHERE cs.user_id = @uid;
DELETE FROM chat_sessions WHERE user_id = @uid;
DELETE FROM crop_scans WHERE user_id = @uid;
DELETE FROM farmer_profiles WHERE user_id = @uid;
DELETE FROM inquiries WHERE user_id = @uid;
DELETE FROM pending_registrations WHERE LOWER(email) = LOWER(@orig_email);

UPDATE reviews SET comment = '[removed]', updated_at = NOW(6) WHERE user_id = @uid;
UPDATE orders SET shipping_line1 = '[removed]', shipping_line2 = NULL, updated_at = NOW(6) WHERE user_id = @uid;
UPDATE bookings SET cancellation_reason = NULL, updated_at = NOW(6) WHERE user_id = @uid;
UPDATE crop_listings SET is_active = FALSE, updated_at = NOW(6) WHERE farmer_id = @uid;

UPDATE users SET name = 'Deleted user', email = CONCAT('deleted-', @uid, '@deleted.invalid'), phone = NULL,
  password_hash = NULL, google_id = NULL, is_active = FALSE, updated_at = NOW(6) WHERE id = @uid;
