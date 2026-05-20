package com.timo.words.modules.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    private String nickname;

    @Column(name = "self_assessed_level")
    private String selfAssessedLevel;

    @Column(name = "difficulty_preference")
    private String difficultyPreference = "standard";

    @Column(name = "exam_type")
    private String examType;

    @Column(name = "target_vocab")
    private Integer targetVocab = 5000;

    @Column(name = "study_days")
    private Integer studyDays = 30;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "daily_new_limit")
    private Integer dailyNewLimit = 20;

    @Column(name = "default_study_mode")
    private String defaultStudyMode = "context_deep";

    @Column(name = "fatigue_reminder")
    private Boolean fatigueReminder = true;

    @Column(name = "schedule_type")
    private String scheduleType = "standard";

    @Column(name = "mu_init")
    private Double muInit = 8.0;

    @Column(name = "sigma_init")
    private Double sigmaInit = 3.0;

    @Column(nullable = false, length = 20)
    private String role = "USER";

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
