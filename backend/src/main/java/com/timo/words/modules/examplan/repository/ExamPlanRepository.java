package com.timo.words.modules.examplan.repository;

import com.timo.words.modules.examplan.entity.ExamPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamPlanRepository extends JpaRepository<ExamPlan, Long> {

    Optional<ExamPlan> findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(Long userId);

    List<ExamPlan> findByUserIdOrderByCreatedAtDesc(Long userId);
}
