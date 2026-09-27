package com.sgkrashi.notification.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * One registered FCM/APNs device token for one user. {@code token} is the
 * unique key (not {@code user_id}+{@code token}) — a physical device's token
 * outlives any one login session, so re-registering the same device (a
 * token refresh, or a different user logging in on it) updates the existing
 * row's {@code userId} rather than accumulating stale duplicates. See
 * {@code DeviceTokenServiceImpl#registerOrUpdate}.
 */
@Entity
@Table(name = "device_tokens")
public class DeviceToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token", nullable = false, unique = true, length = 255)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private DevicePlatform platform;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public DevicePlatform getPlatform() {
        return platform;
    }

    public void setPlatform(DevicePlatform platform) {
        this.platform = platform;
    }
}
