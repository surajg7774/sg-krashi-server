package com.sgkrashi.chatassistant.provider;

import java.util.List;

/**
 * The anti-lock-in seam for this feature — mirrors {@code
 * com.sgkrashi.cropdoctor.provider.CropAnalysisProvider}'s pattern exactly.
 * {@code ChatServiceImpl} depends only on this interface; swapping the
 * underlying model provider is a new implementation of this one method, not
 * a rewrite of the service/controller/persistence layers. {@code
 * GeminiChatProvider} is the only implementation for V1 — not over-built
 * with a registry or factory for a hypothetical second provider that
 * doesn't exist yet.
 */
public interface ChatAssistantProvider {

    /**
     * @param history                prior turns in this session, oldest first (not including {@code newUserMessage})
     * @param newUserMessage         the message just sent
     * @param groundingContext       retrieved platform-knowledge passages to ground the reply in, or {@code null}/blank if none were relevant
     * @param personalDataContext    the authenticated caller's own recent orders/bookings/inquiries, pre-fetched and formatted by {@code ChatServiceImpl} — {@code null} for Guests or when the message wasn't recognized as a personal-data question. This provider never fetches this itself and never receives an identifier it could use to fetch someone else's.
     * @param guestAskedPersonalData true when a Guest's message looks like a personal-data question — the provider should answer by explaining they need to log in, not attempt to guess
     */
    String reply(
            List<ChatTurn> history,
            String newUserMessage,
            String groundingContext,
            String personalDataContext,
            boolean guestAskedPersonalData
    );
}
