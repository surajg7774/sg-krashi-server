-- V3__inquiries_table.sql
-- Module 3 minimal inquiry path: just enough to receive a public contact
-- submission. Extended with module-specific workflow/status transitions in Module 10.

CREATE TABLE inquiries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    module_type VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NULL,
    message TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_inquiries_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_inquiries_module_type ON inquiries (module_type);
CREATE INDEX idx_inquiries_status ON inquiries (status);
