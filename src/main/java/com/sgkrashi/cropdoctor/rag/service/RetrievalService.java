package com.sgkrashi.cropdoctor.rag.service;

import com.sgkrashi.cropdoctor.rag.entity.KnowledgeBaseEntry;

import java.util.List;

/**
 * The "R" in this project's RAG implementation. Metadata-filtered, not
 * embedding-based (see {@code RetrievalServiceImpl}'s Javadoc for why) —
 * retrieval happens on the user's <b>declared</b> crop alone, resolved
 * before the Gemini call is made at all, since the detected problem doesn't
 * exist yet at that point in the pipeline (it's Gemini's own output). A
 * two-pass "identify first, retrieve, then ask again" flow would double the
 * Gemini calls (and cost/latency) for a benefit metadata filtering on the
 * declared crop already gets most of: the retrieved passages ground the
 * model in what's agronomically relevant for that crop, and Gemini itself
 * still decides which of them actually apply to what it sees in the photo.
 */
public interface RetrievalService {

    List<KnowledgeBaseEntry> retrieveForCrop(String declaredCrop, int maxResults);
}
