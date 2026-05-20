package com.timo.words.modules.agent.repository;

import com.timo.words.modules.agent.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    List<ChatMessage> findTop50BySessionIdOrderByCreatedAtAsc(Long sessionId);

    List<ChatMessage> findTop20BySessionIdOrderByCreatedAtDesc(Long sessionId);
}
