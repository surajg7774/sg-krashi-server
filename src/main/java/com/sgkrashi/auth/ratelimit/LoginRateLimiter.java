package com.sgkrashi.auth.ratelimit;

import com.sgkrashi.common.exception.RateLimitExceededException;
import com.sgkrashi.common.ratelimit.FixedWindowRateLimiter;
import com.sgkrashi.common.ratelimit.RateLimitProperties;
import org.springframework.stereotype.Component;

/**
 * Rate limiter for {@code /auth/login} (and mobile login), by default 5 attempts per 15 minutes per
 * client IP; configurable with {@code app.rate-limit.login.max} / {@code .window}. Registration has its own
 * limiter in {@link AuthRateLimiters}. See {@link FixedWindowRateLimiter} for the underlying strategy
 * and its scaling caveat.
 */
@Component
public class LoginRateLimiter {

    private final FixedWindowRateLimiter delegate;

    public LoginRateLimiter(RateLimitProperties properties) {
        RateLimitProperties.Rule rule = properties.login();
        this.delegate = new FixedWindowRateLimiter(rule.max(), rule.window());
    }

    public boolean tryConsume(String key) {
        return delegate.tryConsume(key);
    }

    /** Throws {@link RateLimitExceededException} (429 with {@code Retry-After}) when {@code key} is over the limit. */
    public void enforce(String key) {
        FixedWindowRateLimiter.Decision decision = delegate.acquire(key);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(
                    "Too many attempts. Please try again in a few minutes.", decision.retryAfterSeconds());
        }
    }
}
