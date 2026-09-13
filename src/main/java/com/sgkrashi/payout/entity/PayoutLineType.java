package com.sgkrashi.payout.entity;

/**
 * Distinguishes a normal earned line from a refund reversal. A {@code
 * CLAWBACK} line is created when an order is refunded after its item was
 * already linked into a payout — it never edits or removes the original
 * {@code EARNING} line (that stays as an untouched historical record), it
 * just adds a negative-amount sibling that nets out in whichever batch it
 * lands in. See {@code PayoutService.handleOrderRefunded}.
 */
public enum PayoutLineType {
    EARNING,
    CLAWBACK
}
