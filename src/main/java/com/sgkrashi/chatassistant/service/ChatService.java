package com.sgkrashi.chatassistant.service;

import com.sgkrashi.chatassistant.dto.response.ChatMessageResponse;
import com.sgkrashi.chatassistant.dto.response.ChatSessionResponse;

public interface ChatService {

    /** Works for both Guests and authenticated Customers — {@code ChatSession.userId} is set from the JWT principal if present, left null otherwise. */
    ChatSessionResponse createSession();

    /**
     * @throws com.sgkrashi.common.exception.RateLimitExceededException if the caller (user or IP) has exceeded the chat rate limit
     * @throws com.sgkrashi.common.exception.ResourceNotFoundException if the session doesn't exist, or belongs to a different authenticated user
     */
    ChatMessageResponse sendMessage(Long sessionId, String message, String clientIp);

    /**
     * Authenticated only, ownership-checked — a Guest session (null {@code userId}) or another user's session both 404, indistinguishably.
     * @throws com.sgkrashi.common.exception.ResourceNotFoundException if the session doesn't exist, belongs to no one, or belongs to a different user
     */
    ChatSessionResponse getSession(Long sessionId);
}
