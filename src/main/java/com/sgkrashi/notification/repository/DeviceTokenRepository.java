package com.sgkrashi.notification.repository;

import com.sgkrashi.notification.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserId(Long userId);

    void deleteByToken(String token);

    /** Ownership-scoped delete: removes the row only if it belongs to {@code userId}. Returns how many rows were removed (0 or 1). */
    long deleteByTokenAndUserId(String token, Long userId);
}
