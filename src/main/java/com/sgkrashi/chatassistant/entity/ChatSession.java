package com.sgkrashi.chatassistant.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One chat conversation. {@link #userId} is nullable — Guests get sessions
 * too (they can chat and get grounded answers), same "no owner" convention
 * {@code Inquiry.userId}/{@code CropListing.farmerId} already use. Only
 * authenticated sessions (non-null {@code userId}) can be retrieved later
 * via {@code GET /api/v1/chat/sessions/{id}} — a Guest has no account to
 * come back and look one up with (see {@code ChatController}'s Javadoc).
 */
@Entity
@Table(name = "chat_sessions")
public class ChatSession extends BaseEntity {

    @Column(name = "user_id")
    private Long userId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
