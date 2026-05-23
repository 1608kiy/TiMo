package com.timo.words.modules.agent.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_msg_session", columnList = "session_id")
})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "role", nullable = false)
    private String role; // user, assistant, system

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "card_data", columnDefinition = "TEXT")
    private String cardData; // JSON for structured responses

    @Column(name = "suggested_actions", columnDefinition = "TEXT")
    private String suggestedActions; // JSON array of action buttons

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
