package com.sgkrashi.customer.service;

import com.sgkrashi.customer.dto.request.DeleteAccountRequest;

/** Self-service account deletion for the logged-in user (Google Play / DPDP requirement). */
public interface AccountDeletionService {

    /**
     * Re-authenticates the current user, refuses while orders/bookings/payouts
     * are still in flight, then erases or anonymizes their data.
     *
     * @throws com.sgkrashi.common.exception.ValidationException if the password / Google confirmation is missing or wrong
     * @throws com.sgkrashi.common.exception.BusinessRuleException for admin accounts, or while something is still in flight
     * @throws com.sgkrashi.common.exception.RateLimitExceededException after too many attempts
     */
    void deleteCurrentAccount(DeleteAccountRequest request);
}
