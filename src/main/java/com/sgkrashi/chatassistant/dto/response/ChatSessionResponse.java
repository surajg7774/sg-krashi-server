package com.sgkrashi.chatassistant.dto.response;

import java.util.List;

/** {@code messages} is empty on session creation, populated on {@code GET /api/v1/chat/sessions/{id}}. */
public record ChatSessionResponse(Long id, List<ChatMessageResponse> messages) {
}
