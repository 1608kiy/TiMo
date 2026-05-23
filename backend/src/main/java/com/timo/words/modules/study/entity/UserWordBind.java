package com.timo.words.modules.study.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_word_bind", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "word_id"})
}, indexes = {
        @Index(name = "idx_bind_user_review", columnList = "user_id, next_review_time"),
        @Index(name = "idx_bind_user_stubborn", columnList = "user_id, is_stubborn")
})
public class UserWordBind {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "word_id", nullable = false)
    private Long wordId;

    @Column(name = "difficulty_initial")
    private Double difficultyInitial;

    private Double difficulty;

    private Double stability;

    private Double retrievability;

    @Column(name = "next_review_time")
    private LocalDateTime nextReviewTime;

    @Column(name = "review_count")
    private Integer reviewCount = 0;

    @Column(name = "is_stubborn")
    private Boolean isStubborn = false;

    @Column(name = "stubborn_since")
    private LocalDateTime stubbornSince;

    @Column(name = "consecutive_errors")
    private Integer consecutiveErrors = 0;

    @Column(name = "last_study_time")
    private LocalDateTime lastStudyTime;

    @Column(name = "consecutive_correct_same_mode")
    private Integer consecutiveCorrectSameMode = 0;

    @Column(name = "last_study_mode")
    private String lastStudyMode;

    /**
     * Bitmap of mastered meaning sortOrders (or weights) for polysemous words.
     * Bit i is set when the i-th meaning has been correctly answered in context_deep mode.
     * Supports up to 64 distinct meanings per word (typically 1-5 in real exams).
     * Used by the Step 2 meaning rotation to prioritize unmastered senses.
     */
    @Column(name = "meaning_mastered_mask", nullable = false)
    private Long meaningMasteredMask = 0L;

    /**
     * Timestamp when this word entered the "mastered" state: S > 21 days,
     * recent grade ≥ 3.5, no current error streak. Mastered words drop out of
     * the default review queue (FSRS will naturally re-surface them via
     * nextReviewTime when retrievability actually decays). NULL = not mastered.
     * Cleared back to NULL when the user fails the word post-graduation (grade < 3.0).
     */
    @Column(name = "mastered_at")
    private LocalDateTime masteredAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
