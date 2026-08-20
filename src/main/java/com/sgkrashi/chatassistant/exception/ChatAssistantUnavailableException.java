package com.sgkrashi.chatassistant.exception;

/**
 * Covers every way {@link com.sgkrashi.chatassistant.provider.GeminiChatProvider}
 * can fail to produce a reply — unreachable, timed out, non-2xx, or a
 * malformed response body. Mirrors {@code
 * com.sgkrashi.cropdoctor.exception.AiServiceUnavailableException} exactly;
 * kept as its own class rather than reused since the two features are
 * unrelated beyond both calling Gemini.
 */
public class ChatAssistantUnavailableException extends RuntimeException {

    public ChatAssistantUnavailableException(String message) {
        super(message);
    }

    public ChatAssistantUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
