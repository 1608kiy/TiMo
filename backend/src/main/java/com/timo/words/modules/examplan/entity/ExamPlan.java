package com.timo.words.modules.examplan.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "exam_plans", indexes = {
        @Index(name = "idx_exam_plan_user_active", columnList = "user_id, is_active")
})
public class ExamPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "exam_type")
    private String examType;

    @Column(name = "target_vocab")
    private Integer targetVocab;

    @Column(name = "daily_new_words")
    private Integer dailyNewWords;

    @Column(name = "daily_review_words")
    private Integer dailyReviewWords;

    @Column(name = "estimated_days")
    private Integer estimatedDays;

    @Column(name = "study_days_per_week")
    private Integer studyDaysPerWeek;

    @Column(name = "daily_hours")
    private Double dailyHours;

    @Column(name = "plan_json", columnDefinition = "TEXT")
    private String planJson;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
