package com.sgkrashi.cropdoctor.ratelimit;

import com.sgkrashi.common.ratelimit.FixedWindowRateLimiter;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Rate limiter for {@code POST /api/v1/ai/crop-doctor/analyze} (max 10
 * analyses per hour per user — each call costs real inference compute/time,
 * unlike a login attempt). Keyed by user ID rather than client IP: this
 * endpoint is only reachable authenticated, so the account is the
 * meaningful unit to limit, not the network address. See
 * {@link FixedWindowRateLimiter} for the underlying strategy.
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
