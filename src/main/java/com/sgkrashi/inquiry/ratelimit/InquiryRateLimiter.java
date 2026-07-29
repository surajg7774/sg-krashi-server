package com.sgkrashi.inquiry.ratelimit;

import com.sgkrashi.common.ratelimit.FixedWindowRateLimiter;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Rate limiter for {@code /inquiries} (max 3 submissions per 10 minutes per
 * client IP). Tighter than {@code LoginRateLimiter} since this is an
 * unauthenticated public write endpoint and a plausible spam target.
 */
@Component
public class InquiryRateLimiter {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final FixedWindowRateLimiter delegate = new FixedWindowRateLimiter(MAX_ATTEMPTS, WINDOW);

    public boolean tryConsume(String key) {
        return delegate.tryConsume(key);
    }
}
