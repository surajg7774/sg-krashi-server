-- V17__audit_log_cms.sql
-- Module 17: Audit logging (retroactive) + CMS content blocks.

CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    action VARCHAR(60) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id BIGINT NULL,
    before_json TEXT NULL,
    after_json TEXT NULL,
    ip_address VARCHAR(45) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_audit_logs_admin FOREIGN KEY (admin_id) REFERENCES users (id)
);

CREATE INDEX idx_audit_logs_admin_id ON audit_logs (admin_id);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);

-- content_key is unique (the stable identifier the public frontend fetches
-- by, e.g. "homepage_hero") — not a display name.
CREATE TABLE content_blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_key VARCHAR(100) NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL,
    payload_json TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_content_blocks_type ON content_blocks (type);

-- Seeds Module 3's ACTUAL current hardcoded homepage copy (HeroSection.tsx /
-- TestimonialCarousel.tsx) as real, Admin-editable rows — the migration path
-- from hardcoded to CMS-driven, not a fresh start with placeholder content.
-- hero.jpg was copied to the frontend's public/images/ so it's servable at a
-- stable, unhashed path regardless of the Vite build.
INSERT INTO content_blocks (content_key, type, payload_json, sort_order, created_at, updated_at, is_active) VALUES
('homepage_hero', 'BANNER', '{"title":"One Platform for Every Part of Your Farm Business","subtitle":"Rent equipment, book a farmhouse stay, buy organic produce, sell your crop directly, and more — SG Krashi brings the whole farm economy onto one trusted platform.","imageUrl":"/images/hero.jpg","imageAlt":"Tractor working a field at sunrise, representing SG Krashi''s farm equipment and services","ctaText":"Rent Equipment","ctaLink":"/equipment-rental","secondaryCtaText":"Book a Farm Stay","secondaryCtaLink":"/farm-stay","stats":[{"value":"500+","label":"Happy Farmers"},{"value":"150+","label":"Equipment Bookings"},{"value":"4.9★","label":"Customer Rating"}]}', 0, NOW(6), NOW(6), TRUE),

('testimonial_ramesh', 'TESTIMONIAL', '{"authorName":"Ramesh Patil","role":"Wheat Farmer, Nashik","quote":"Renting a tractor used to mean a week of phone calls and paperwork. On SG Krashi I booked one online and it arrived the next morning."}', 0, NOW(6), NOW(6), TRUE),

('testimonial_anjali', 'TESTIMONIAL', '{"authorName":"Anjali Deshmukh","role":"Farm Stay Guest","quote":"The farm stay was exactly the break my family needed. My kids fed the goats every morning and we ate dinner grown steps away from our room."}', 1, NOW(6), NOW(6), TRUE),

('testimonial_suresh', 'TESTIMONIAL', '{"authorName":"Suresh Yadav","role":"Vegetable Grower, Pune","quote":"I sell my tomatoes through the Crop Marketplace now instead of haggling with a middleman at the mandi. I see the going rate before I agree to anything."}', 2, NOW(6), NOW(6), TRUE);
