package com.sgkrashi.chatassistant.repository;

import com.sgkrashi.chatassistant.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
}
