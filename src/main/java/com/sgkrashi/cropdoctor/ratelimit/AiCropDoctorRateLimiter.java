package com.sgkrashi.cropdoctor.ratelimit;

import com.sgkrashi.common.ratelimit.FixedWindowRateLimiter;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Rate limiter for {@code POST /api/v1/ai/crop-doctor/analyze} (max 10
 * analyses per hour per key — each call costs real inference compute/time,
 * unlike a login attempt). The endpoint is public (Guest Access refinement),
 * so the caller ({@code CropDoctorServiceImpl}) picks the key: {@code
 * "user:" + userId} when authenticated, {@code "ip:" + clientIp} for Guests,
 * since there's no account to key by and the endpoint calls a metered
 * external API. See {@link FixedWindowRateLimiter} for the underlying
 * strategy.
 */
@Component
public class AiCropDoctorRateLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final FixedWindowRateLimiter delegate = new FixedWindowRateLimiter(MAX_ATTEMPTS, WINDOW);

    public boolean tryConsume(String key) {
        return delegate.tryConsume(key);
    }
}
