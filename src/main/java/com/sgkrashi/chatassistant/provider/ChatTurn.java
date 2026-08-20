package com.sgkrashi.chatassistant.provider;

/** One provider-agnostic conversation turn — {@code role} is {@code "user"} or {@code "assistant"}, not the {@code ChatMessageRole} entity enum, so this package has no JPA dependency. */
public record ChatTurn(String role, String content) {
}
