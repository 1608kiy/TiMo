package com.timo.words.modules.agent.repository;

import com.timo.words.modules.agent.entity.ConversationQuizLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ConversationQuizLogRepository extends JpaRepository<ConversationQuizLog, Long> {

    List<ConversationQuizLog> findByUserIdAndWordIdAndUsedCorrectlyAndCreatedAtAfter(
            Long userId, Long wordId, Boolean usedCorrectly, LocalDateTime since);

    long countByUserIdAndUsedCorrectlyAndCreatedAtAfter(
            Long userId, Boolean usedCorrectly, LocalDateTime since);

    @Query("SELECT DISTINCT l.wordId FROM ConversationQuizLog l " +
           "WHERE l.userId = :userId AND l.usedCorrectly = true AND l.createdAt >= :since")
    List<Long> findWordIdsUsedCorrectlySince(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since);
}
