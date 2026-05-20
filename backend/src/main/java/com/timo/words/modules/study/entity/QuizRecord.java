package com.timo.words.modules.study.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "quiz_records", indexes = {
        @Index(name = "idx_quiz_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_quiz_user_word", columnList = "user_id, word_id")
})
public class QuizRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "word_id", nullable = false)
    private Long wordId;

    @Column(name = "study_mode", nullable = false)
    private String studyMode;

    private Double grade;

    @Column(name = "composite_grade")
    private Double compositeGrade;

    @Column(name = "step_results", columnDefinition = "JSON")
    private String stepResults;

    @Column(name = "hint_total")
    private Integer hintTotal = 0;

    @Column(name = "reaction_time_ms")
    private Integer reactionTimeMs;

    private String source = "static";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
