package com.sgkrashi.cropdoctor.provider;

/**
 * One knowledge-base entry that was actually retrieved and included in the
 * prompt for a given scan — never a fabricated citation. Deliberately
 * provider-agnostic (lives next to {@link CropAnalysisResult}, not inside
 * {@code com.sgkrashi.cropdoctor.rag}) since a future non-RAG provider
 * simply returns an empty list here, the same way {@code LocalModelProvider}
 * does today.
 */
public record GroundingSource(String title, String crop, String topic) {
}
