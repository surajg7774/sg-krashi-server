package com.sgkrashi.cropdoctor.rag.service.impl;

/**
 * Unchecked on purpose — a failed embedding call is never fatal to a scan.
 * {@code RetrievalServiceImpl} catches this and falls back to metadata
 * matching; nothing else in the call chain (the scan itself, {@code
 * GeminiAnalysisProvider}) needs to know embeddings were even attempted.
 */
class EmbeddingUnavailableException extends RuntimeException {

    EmbeddingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
