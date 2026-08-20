package com.sgkrashi.chatassistant.ratelimit;

import com.sgkrashi.common.ratelimit.FixedWindowRateLimiter;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Rate limiter for {@code POST /api/v1/chat/sessions/{id}/messages} — same
 * reasoning as {@code AiCropDoctorRateLimiter}: each message costs a real
 * Gemini call (and, when grounded, an embedding call too), and the endpoint
 * is public, so the caller ({@code ChatServiceImpl}) picks the key: {@code
 * "user:" + userId} when authenticated, {@code "ip:" + clientIp} for Guests.
 * A higher limit than AI Crop Doctor's 10/hour is appropriate here — a real
 * conversation is many short messages, not one expensive analysis each.
 */
@Component
public class ChatRateLimiter {

    private static final int MAX_ATTEMPTS = 30;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final FixedWindowRateLimiter delegate = new FixedWindowRateLimiter(MAX_ATTEMPTS, WINDOW);

    public boolean tryConsume(String key) {
        return delegate.tryConsume(key);
    }
}
