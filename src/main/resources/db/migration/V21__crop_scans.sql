-- V21__crop_scans.sql
-- AI Crop Doctor: stores each user's crop/plant disease scan history. The
-- uploaded image goes through the existing StorageProvider abstraction
-- (Module 5) exactly like every other upload in this project — only the
-- resulting URL is persisted here, never image bytes.

CREATE TABLE crop_scans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    crop_name VARCHAR(100) NOT NULL,
    disease_name VARCHAR(150) NOT NULL,
    confidence_score DECIMAL(5,4) NOT NULL,
    severity VARCHAR(50) NULL,
    recommendation TEXT NOT NULL,
    model_version VARCHAR(100) NOT NULL,
    is_uncertain BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_crop_scans_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_crop_scans_user_id ON crop_scans (user_id);
