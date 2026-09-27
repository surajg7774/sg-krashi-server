package com.sgkrashi.notification.service.impl;

import com.sgkrashi.notification.entity.DevicePlatform;
import com.sgkrashi.notification.entity.DeviceToken;
import com.sgkrashi.notification.repository.DeviceTokenRepository;
import com.sgkrashi.notification.service.DeviceTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceTokenServiceImpl implements DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    public DeviceTokenServiceImpl(DeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    @Override
    @Transactional
    public void registerOrUpdate(Long userId, String token, DevicePlatform platform) {
        DeviceToken deviceToken = deviceTokenRepository.findByToken(token).orElseGet(DeviceToken::new);
        deviceToken.setToken(token);
        deviceToken.setUserId(userId);
        deviceToken.setPlatform(platform);
        deviceTokenRepository.save(deviceToken);
    }

    @Override
    @Transactional
    public void unregister(String token) {
        deviceTokenRepository.deleteByToken(token);
    }
}
