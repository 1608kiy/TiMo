package com.timo.words.modules.agent.repository;

import com.timo.words.modules.agent.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<ChatSession> findFirstByUserIdAndConversationTypeOrderByCreatedAtDesc(Long userId, String type);
}
