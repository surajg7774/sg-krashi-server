package com.sgkrashi.chatassistant.controller;

import com.sgkrashi.chatassistant.dto.response.ChatConfigResponse;
import com.sgkrashi.common.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets the frontend — a separate Vercel deployment with no visibility into
 * Railway's env vars — know whether the chat assistant is currently enabled,
 * so {@code ChatWidget} can hide the floating button entirely rather than
 * just disabling it. Public: this is a boolean feature flag, not user data.
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatConfigController {

    private final boolean chatAssistantEnabled;

    public ChatConfigController(@Value("${app.chat-assistant.enabled:true}") boolean chatAssistantEnabled) {
        this.chatAssistantEnabled = chatAssistantEnabled;
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<ChatConfigResponse>> getConfig() {
        return ResponseEntity.ok(ApiResponse.success(new ChatConfigResponse(chatAssistantEnabled), "Chat config retrieved"));
    }
}
