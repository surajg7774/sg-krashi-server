package com.sgkrashi.ai.embedding;

/**
 * Wraps Gemini's embedding API — genuinely domain-agnostic (arbitrary text
 * in, a vector out), which is why this lives outside {@code
 * com.sgkrashi.cropdoctor} even though AI Crop Doctor's RAG was its first
 * caller: {@code com.sgkrashi.chatassistant}'s platform-knowledge retrieval
 * is a second, unrelated consumer, and neither has any business depending on
 * the other's package. Document and query embeddings are deliberately
 * separate methods, not one {@code embed(text)}: Gemini's {@code taskType}
 * parameter produces different (asymmetric) vectors depending on whether the
 * text is something being indexed ({@code RETRIEVAL_DOCUMENT}) or something
 * being searched for ({@code RETRIEVAL_QUERY}) — using the wrong one for
 * either side measurably hurts retrieval quality, per Google's own
 * documentation.
 */
public interface EmbeddingService {

    /** For knowledge-base-style content at ingest time (crop disease entries, platform FAQ entries, etc). */
    float[] embedDocument(String text);

    /** For a retrieval query built at request time. */
    float[] embedQuery(String text);
}
