package com.sgkrashi.ai.embedding;

/**
 * Shared by every embedding-based retrieval path in this codebase (AI Crop
 * Doctor's knowledge base, the chat assistant's platform knowledge base) —
 * extracted here rather than duplicated in each, since it's the same exact
 * math regardless of which knowledge base is being searched.
 */
public final class CosineSimilarity {

    private CosineSimilarity() {
    }

    /**
     * cos(theta) between two vectors = (A . B) / (|A| * |B|) — the dot
     * product of the two vectors divided by the product of their magnitudes
     * (Euclidean lengths). Ranges from -1 (opposite) to 1 (identical
     * direction); two embeddings of semantically similar text end up
     * "pointing the same way" in the embedding space, so a higher value
     * means more similar meaning, independent of either vector's raw scale.
     * A zero-length or mismatched-length vector can't be compared
     * meaningfully, so it's scored as dissimilar (0.0) rather than risking a
     * divide-by-zero or an out-of-bounds access.
     */
    public static double compute(float[] a, float[] b) {
        if (a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double magnitudeA = 0.0;
        double magnitudeB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            magnitudeA += a[i] * a[i];
            magnitudeB += b[i] * b[i];
        }
        if (magnitudeA == 0.0 || magnitudeB == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(magnitudeA) * Math.sqrt(magnitudeB));
    }
}
