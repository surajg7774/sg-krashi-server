-- Cached daily mandi (market) commodity prices, synced from data.gov.in's
-- Agmarknet dataset by MandiPriceSyncJob. UNIQUE(commodity, market_name,
-- price_date) is the idempotency guard: re-running the sync job (its own
-- schedule, or an admin's manual trigger) always upserts, never duplicates.
CREATE TABLE mandi_prices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    commodity VARCHAR(100) NOT NULL,
    market_name VARCHAR(150) NOT NULL,
    state VARCHAR(100) NOT NULL,
    district VARCHAR(100) NULL,
    min_price DECIMAL(10, 2) NOT NULL,
    max_price DECIMAL(10, 2) NOT NULL,
    modal_price DECIMAL(10, 2) NOT NULL,
    price_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_mandi_price_row UNIQUE (commodity, market_name, price_date)
);

CREATE INDEX idx_mandi_prices_commodity ON mandi_prices (commodity);
CREATE INDEX idx_mandi_prices_state ON mandi_prices (state);
CREATE INDEX idx_mandi_prices_date ON mandi_prices (price_date);
