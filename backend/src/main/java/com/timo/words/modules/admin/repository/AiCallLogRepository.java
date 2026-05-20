package com.timo.words.modules.admin.repository;

import com.timo.words.modules.admin.entity.AiCallLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long> {

    Page<AiCallLog> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<AiCallLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT COUNT(a) FROM AiCallLog a WHERE a.createdAt >= :since")
    long countSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM AiCallLog a WHERE a.status = :status AND a.createdAt >= :since")
    long countByStatusSince(@Param("status") String status, @Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(a.totalTokens), 0) FROM AiCallLog a WHERE a.createdAt >= :since")
    long sumTokensSince(@Param("since") LocalDateTime since);

    @Query("SELECT FUNCTION('DATE', a.createdAt) as day, COUNT(a), COALESCE(SUM(a.totalTokens), 0) " +
           "FROM AiCallLog a WHERE a.createdAt >= :since GROUP BY FUNCTION('DATE', a.createdAt) ORDER BY day")
    List<Object[]> dailyStatsSince(@Param("since") LocalDateTime since);
}
