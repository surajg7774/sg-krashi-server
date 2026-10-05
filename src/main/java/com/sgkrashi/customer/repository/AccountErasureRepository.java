package com.sgkrashi.customer.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * The SQL behind account deletion, in one reviewable place. Plain SQL on
 * purpose: one account's data is spread over ~20 tables, none of which should
 * be loaded as entities just to be thrown away, and the users row is
 * <b>anonymized in place, never deleted</b> — orders, payments, bookings,
 * farmer payouts and admin audit rows all point at it, and all of those must
 * survive (detached from the person) for accounting.
 *
 * <p>What is deleted vs kept is the product decision recorded in the
 * deletion write-up and the public /delete-account page; change them
 * together.
 */
@Repository
public class AccountErasureRepository {

    /** A payment/booking that was only just started may still complete, so it blocks deletion for this long. */
    private static final String RECENT = "NOW(6) - INTERVAL 1 DAY";

    private final JdbcTemplate jdbc;

    public AccountErasureRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Human-readable reasons deletion must wait; empty when nothing is in flight. */
    public List<String> openObligations(long userId) {
        List<String> reasons = new ArrayList<>();
        if (count("SELECT COUNT(*) FROM orders WHERE user_id = ? AND (status IN ('CONFIRMED', 'SHIPPED') "
                + "OR (status = 'PENDING_PAYMENT' AND created_at > " + RECENT + "))", userId) > 0) {
            reasons.add("an order that hasn't been delivered yet");
        }
        if (count("SELECT COUNT(*) FROM bookings WHERE user_id = ? AND ((status = 'CONFIRMED' AND end_date >= CURDATE()) "
                + "OR (status = 'PENDING_PAYMENT' AND created_at > " + RECENT + "))", userId) > 0) {
            reasons.add("an upcoming booking");
        }
        if (count("SELECT COUNT(*) FROM farmer_payouts WHERE farmer_id = ? AND status IN ('BATCHED', 'APPROVED')", userId) > 0) {
            reasons.add("a farmer payout that hasn't been paid out yet");
        }
        return reasons;
    }

    /** Raw {@code image_url} / {@code image_urls} (JSON array) values of the user's crop scans, to be removed from image storage. */
    public List<String> findScanImageColumns(long userId) {
        List<String> values = new ArrayList<>();
        jdbc.query("SELECT image_url, image_urls FROM crop_scans WHERE user_id = ?", rs -> {
            values.add(rs.getString(1));
            String json = rs.getString(2);
            if (json != null) {
                values.add(json);
            }
        }, userId);
        return values;
    }

    /**
     * All-or-nothing: either the whole account is erased/anonymized or none of
     * it. Order matters only for foreign keys (children before parents).
     */
    @Transactional
    public void erase(long userId, String originalEmail, String placeholderEmail) {
        // ---- deleted outright ----
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM device_tokens WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM addresses WHERE user_id = ?", userId);
        jdbc.update("DELETE ci FROM cart_items ci JOIN carts c ON ci.cart_id = c.id WHERE c.user_id = ?", userId);
        jdbc.update("DELETE FROM carts WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
        jdbc.update("DELETE cm FROM chat_messages cm JOIN chat_sessions cs ON cm.session_id = cs.id WHERE cs.user_id = ?", userId);
        jdbc.update("DELETE FROM chat_sessions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM crop_scans WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM farmer_profiles WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM inquiries WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM pending_registrations WHERE LOWER(email) = LOWER(?)", originalEmail);

        // ---- kept, but with the personal parts removed ----
        jdbc.update("UPDATE reviews SET comment = '[removed]', updated_at = NOW(6) WHERE user_id = ?", userId);
        jdbc.update("UPDATE orders SET shipping_line1 = '[removed]', shipping_line2 = NULL, updated_at = NOW(6) WHERE user_id = ?", userId);
        jdbc.update("UPDATE bookings SET cancellation_reason = NULL, updated_at = NOW(6) WHERE user_id = ?", userId);
        jdbc.update("UPDATE crop_listings SET is_active = FALSE, updated_at = NOW(6) WHERE farmer_id = ?", userId);

        // ---- the account itself: anonymized in place, and deactivated so every
        // token and login attempt is refused immediately (the login check and
        // the JWT filter both honor is_active) ----
        jdbc.update("UPDATE users SET name = 'Deleted user', email = ?, phone = NULL, password_hash = NULL, "
                + "google_id = NULL, is_active = FALSE, updated_at = NOW(6) WHERE id = ?", placeholderEmail, userId);
    }

    private long count(String sql, long userId) {
        Long value = jdbc.queryForObject(sql, Long.class, userId);
        return value == null ? 0 : value;
    }
}
