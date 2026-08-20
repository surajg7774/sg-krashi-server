package com.sgkrashi.ai.embedding;

/**
 * Unchecked on purpose — a failed embedding call should never be fatal to
 * whatever feature triggered it. Callers (crop-doctor RAG retrieval, chat
 * assistant platform-knowledge retrieval) catch this and fall back to a
 * cruder retrieval strategy or no grounding at all, exactly like a Gemini
 * analysis timeout degrades gracefully elsewhere in this codebase.
 */
public class EmbeddingUnavailableException extends RuntimeException {

    public EmbeddingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
