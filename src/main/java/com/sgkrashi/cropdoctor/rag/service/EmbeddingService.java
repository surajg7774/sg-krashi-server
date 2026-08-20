package com.sgkrashi.cropdoctor.rag.service;

/**
 * Wraps Gemini's embedding API — {@code RetrievalServiceImpl} calls this,
 * never touches embedding-model details (endpoint, task type, dimension)
 * directly. Document and query embeddings are deliberately separate methods,
 * not one {@code embed(text)}: Gemini's {@code taskType} parameter produces
 * different (asymmetric) vectors depending on whether the text is something
 * being indexed ({@code RETRIEVAL_DOCUMENT}) or something being searched for
 * ({@code RETRIEVAL_QUERY}) — using the wrong one for either side measurably
 * hurts retrieval quality, per Google's own documentation.
 */
public interface EmbeddingService {

    /** For {@link com.sgkrashi.cropdoctor.rag.entity.KnowledgeBaseEntry} content at ingest time. */
    float[] embedDocument(String text);

    /** For the retrieval query built at scan time (currently just the declared crop). */
    float[] embedQuery(String text);
}
