package com.sgkrashi.chatassistant.dto.response;

import java.time.Instant;

public record ChatMessageResponse(Long id, String role, String content, Instant createdAt) {
}
