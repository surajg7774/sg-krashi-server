package com.sgkrashi.advisory.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** A farmer's location + weather-advisory opt-in. One row per farmer, created lazily on first settings save. */
@Entity
@Table(name = "farmer_profiles")
public class FarmerProfile extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "place_name", length = 150)
    private String placeName;

    @Column(name = "weather_advisory_opt_in", nullable = false)
    private boolean weatherAdvisoryOptIn = false;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public String getPlaceName() {
        return placeName;
    }

    public void setPlaceName(String placeName) {
        this.placeName = placeName;
    }

    public boolean isWeatherAdvisoryOptIn() {
        return weatherAdvisoryOptIn;
    }

    public void setWeatherAdvisoryOptIn(boolean weatherAdvisoryOptIn) {
        this.weatherAdvisoryOptIn = weatherAdvisoryOptIn;
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }
}
