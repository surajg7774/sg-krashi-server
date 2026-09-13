-- V29__farmer_payouts.sql
-- Farmer Payout System: a weekly batch job aggregates each farmer's
-- DELIVERED crop-listing order items into a payout row (95% net, 5%
-- platform commission), an Admin approves it, then marks it paid after a
-- manual bank transfer outside the app. Scoped to CROP_LISTING order items
-- only in this pass — Equipment/StayListing bookings have no farmer_id
-- anywhere in the schema, so they cannot be attributed to a farmer yet;
-- that is a separate future task, not addressed here.

CREATE TABLE farmer_payouts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    farmer_id BIGINT NOT NULL,
    -- Bookkeeping only (the span since this batch was opened until it was
    -- last added to) — never a query filter. The weekly job sweeps whatever
    -- is currently unbatched each run rather than filtering by a calendar
    -- window, so an item can never be permanently missed by falling just
    -- outside a boundary.
    cycle_start_date DATE NOT NULL,
    cycle_end_date DATE NOT NULL,
    -- Always a derived sum of this row's own farmer_payout_lines — never
    -- accumulated independently, so these can never drift from what the
    -- line items actually add up to.
    gross_amount DECIMAL(12,2) NOT NULL,
    commission_amount DECIMAL(12,2) NOT NULL,
    net_amount DECIMAL(12,2) NOT NULL,
    -- BATCHED -> APPROVED -> PAID. No PENDING row state: "pending/accrued"
    -- (earned but not yet swept into a batch) is computed live from
    -- unbatched order_items, never persisted here.
    status VARCHAR(20) NOT NULL,
    approved_by BIGINT NULL,
    approved_at TIMESTAMP(6) NULL,
    paid_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_farmer_payouts_farmer FOREIGN KEY (farmer_id) REFERENCES users (id),
    CONSTRAINT fk_farmer_payouts_approved_by FOREIGN KEY (approved_by) REFERENCES users (id)
);

CREATE INDEX idx_farmer_payouts_farmer_id ON farmer_payouts (farmer_id);
CREATE INDEX idx_farmer_payouts_status ON farmer_payouts (status);

-- Append-only, same "immutable audit-trail row" shape as
-- order_status_history — no updated_at/is_active, never updated once
-- created. A refund after an item is already linked here never edits its
-- row; it adds a new CLAWBACK sibling instead (see FarmerPayoutLine's
-- Javadoc).
CREATE TABLE farmer_payout_lines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payout_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    line_type VARCHAR(20) NOT NULL,
    -- Negative for a CLAWBACK line.
    gross_amount DECIMAL(12,2) NOT NULL,
    commission_amount DECIMAL(12,2) NOT NULL,
    net_amount DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_farmer_payout_lines_payout FOREIGN KEY (payout_id) REFERENCES farmer_payouts (id),
    CONSTRAINT fk_farmer_payout_lines_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    -- At most one EARNING line ever per order_item (the weekly job's
    -- eligibility query already excludes already-linked items; this is the
    -- defensive DB-level backstop against double-counting on a re-run), and
    -- at most one CLAWBACK line ever per order_item (same backstop against
    -- a refund event somehow firing twice) — but legitimately both an
    -- EARNING and a CLAWBACK row for the same item if it was paid out and
    -- later refunded, hence unique on the pair rather than on
    -- order_item_id alone.
    CONSTRAINT uq_farmer_payout_lines_item_type UNIQUE (order_item_id, line_type)
);

CREATE INDEX idx_farmer_payout_lines_payout_id ON farmer_payout_lines (payout_id);
