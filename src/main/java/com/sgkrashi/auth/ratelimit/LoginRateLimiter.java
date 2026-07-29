package com.sgkrashi.auth.ratelimit;

import com.sgkrashi.common.ratelimit.FixedWindowRateLimiter;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Rate limiter for {@code /auth/login} and {@code /auth/register} (max 5 attempts
 * per 15 minutes per client IP). See {@link FixedWindowRateLimiter} for the
 * underlying strategy and its scaling caveat.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final FixedWindowRateLimiter delegate = new FixedWindowRateLimiter(MAX_ATTEMPTS, WINDOW);

    public boolean tryConsume(String key) {
        return delegate.tryConsume(key);
    }
}
