package com.sgkrashi.chatassistant.exception;

/**
 * Specifically Gemini's own HTTP 429 (account/key-level quota exhausted),
 * distinguished from every other failure {@link ChatAssistantUnavailableException}
 * covers — mirrors {@code com.sgkrashi.cropdoctor.exception.AiQuotaExceededException}.
 * Worth a distinct type: chat, AI Crop Doctor analysis, and both knowledge
 * bases' embeddings all share one Gemini API key, so a quota exhaustion here
 * is genuinely a different, more specific situation ("wait for quota to
 * reset") than a generic unreachable/malformed-response failure — and a
 * generic {@code CHAT_ASSISTANT_UNAVAILABLE} for both was real friction to
 * diagnose live (a 429 on this shared key can look identical to a real bug
 * from the client's point of view until someone specifically checks for it).
 */
public class ChatQuotaExceededException extends RuntimeException {

    public ChatQuotaExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
