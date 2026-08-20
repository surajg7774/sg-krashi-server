-- V27__knowledge_base_embeddings.sql
-- Adds storage for a per-entry embedding vector, enabling semantic (cosine
-- similarity) retrieval alongside the existing exact-crop-match filter.
-- Nullable and no backfill here: MySQL as installed on this project has no
-- native vector type, and generating 28 embeddings requires a real Gemini
-- API call per entry, which doesn't belong in a schema migration. The
-- KnowledgeBaseEmbeddingBackfillRunner (ApplicationRunner) fills existing
-- NULL rows in automatically the first time the app boots after this
-- migration; new entries get theirs generated at creation time in
-- KnowledgeBaseIngestionServiceImpl.
ALTER TABLE knowledge_base_entries
    ADD COLUMN embedding JSON NULL AFTER content;
