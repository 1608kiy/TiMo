package com.timo.words.modules.admin.repository;

import com.timo.words.modules.admin.entity.AdminOperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;

public interface AdminOperationLogRepository extends JpaRepository<AdminOperationLog, Long>, JpaSpecificationExecutor<AdminOperationLog> {
    Page<AdminOperationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<AdminOperationLog> findByOperationTypeOrderByCreatedAtDesc(String operationType, Pageable pageable);
    Page<AdminOperationLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end, Pageable pageable);
    Page<AdminOperationLog> findByOperationTypeAndCreatedAtBetweenOrderByCreatedAtDesc(String operationType, LocalDateTime start, LocalDateTime end, Pageable pageable);
}
