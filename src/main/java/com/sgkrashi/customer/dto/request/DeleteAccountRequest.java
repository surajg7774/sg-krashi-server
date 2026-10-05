package com.sgkrashi.customer.dto.request;

/**
 * Re-authentication for account deletion: the account's password, or — for
 * an account that signs in with Google — a freshly obtained Google ID token.
 * Exactly one is needed; both are optional here because which one applies
 * depends on the account, which the service knows.
 */
public record DeleteAccountRequest(String password, String googleIdToken) {
}
