package com.sgkrashi.cropdoctor.rag.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Shape of {@code models.<model>:embedContent}'s response — singular {@code embedding}, not the plural {@code embeddings} array {@code batchEmbedContents} returns. */
@JsonIgnoreProperties(ignoreUnknown = true)
record EmbeddingApiResponse(Embedding embedding) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Embedding(float[] values) {
    }
}
