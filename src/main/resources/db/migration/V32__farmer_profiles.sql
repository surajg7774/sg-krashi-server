-- One-to-one extension row per farmer (created lazily on first settings
-- save, not eagerly for every FARMER-role user) — location for the weather
-- advisory job, and the opt-in toggle itself. No Farmer entity/table
-- existed before this; "farmer" was purely the FARMER role on users.
CREATE TABLE farmer_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    latitude DECIMAL(9, 6) NULL,
    longitude DECIMAL(9, 6) NULL,
    place_name VARCHAR(150) NULL,
    weather_advisory_opt_in BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_farmer_profiles_user FOREIGN KEY (user_id) REFERENCES users (id)
);
