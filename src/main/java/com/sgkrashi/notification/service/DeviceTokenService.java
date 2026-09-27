package com.sgkrashi.notification.service;

import com.sgkrashi.notification.entity.DevicePlatform;

public interface DeviceTokenService {

    /** Upserts by token — see {@code DeviceToken}'s Javadoc for why token, not user+token, is the identity. */
    void registerOrUpdate(Long userId, String token, DevicePlatform platform);

    /** Called on logout so a signed-out device stops receiving pushes meant for the account that just logged out. */
    void unregister(String token);
}
