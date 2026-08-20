package com.sgkrashi.chatassistant.exception;

/**
 * Thrown when {@code app.chat-assistant.enabled=false} (the {@code
 * CHAT_ASSISTANT_ENABLED} kill switch) and a session/message endpoint is hit
 * anyway — defense in depth for direct API calls, since the frontend
 * shouldn't even render the widget in this state (see {@code
 * ChatConfigController}). Distinct from {@link ChatQuotaExceededException}:
 * this is a deliberate admin choice, not Gemini running out of quota, even
 * though both surface to the client as "unavailable right now."
 */
public class ChatAssistantDisabledException extends RuntimeException {

    public ChatAssistantDisabledException(String message) {
        super(message);
    }
}
