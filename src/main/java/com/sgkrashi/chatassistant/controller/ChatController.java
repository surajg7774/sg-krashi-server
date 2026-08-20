package com.sgkrashi.chatassistant.controller;

import com.sgkrashi.chatassistant.dto.request.SendMessageRequest;
import com.sgkrashi.chatassistant.dto.response.ChatMessageResponse;
import com.sgkrashi.chatassistant.dto.response.ChatSessionResponse;
import com.sgkrashi.chatassistant.service.ChatService;
import com.sgkrashi.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST} endpoints are public (Guests and Customers both chat) — see
 * {@code SecurityConfig}'s {@code PUBLIC_POST_ENDPOINTS}, same pattern as AI
 * Crop Doctor's {@code /analyze}. {@code GET /sessions/{id}} is deliberately
 * NOT in any public list — a Guest has no account to come back and retrieve
 * a past session with, so this falls through to the default {@code
 * anyRequest().authenticated()} rule, same as every other "my own data"
 * endpoint in this codebase.
 */
@RestController
@RequestMapping("/api/v1/chat/sessions")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ChatSessionResponse>> createSession() {
        ChatSessionResponse response = chatService.createSession();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Chat session created"));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(
            @PathVariable Long id,
            @Valid @RequestBody SendMessageRequest request,
            HttpServletRequest httpRequest
    ) {
        // Only actually used for Guest requests (see ChatServiceImpl) — an
        // authenticated request's rate-limit key comes from the JWT
        // principal instead, same as AI Crop Doctor's /analyze.
        ChatMessageResponse response = chatService.sendMessage(id, request.message(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success(response, "Message sent"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ChatSessionResponse>> getSession(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(chatService.getSession(id), "Chat session retrieved"));
    }
}
