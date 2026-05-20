-- ============================================
-- timo_words 数据库建表脚本
-- 基于 AI Agent 与 FSRS 的自适应语境化背单词系统
-- ============================================

CREATE DATABASE IF NOT EXISTS timo_words DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE timo_words;

-- -------------------------------------------
-- 1. 用户相关
-- -------------------------------------------

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50),
    self_assessed_level VARCHAR(20) COMMENT 'CET4/CET6/考研/GRE',
    difficulty_preference VARCHAR(20) DEFAULT 'standard' COMMENT 'conservative/standard/aggressive',
    exam_type VARCHAR(20),
    target_vocab INT DEFAULT 5000,
    study_days INT DEFAULT 30,
    schedule_type VARCHAR(20) DEFAULT 'standard',
    mu_init DOUBLE DEFAULT 8.0,
    sigma_init DOUBLE DEFAULT 3.0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_preferences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    default_mode VARCHAR(30) DEFAULT 'quick_memory',
    daily_new_limit INT DEFAULT 20,
    reminder_enabled BOOLEAN DEFAULT TRUE,
    fatigue_sensitivity INT DEFAULT 20 COMMENT '疲劳提醒阈值（分钟）',
    agent_chat_enabled BOOLEAN DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- -------------------------------------------
-- 2. 词库相关
-- -------------------------------------------

CREATE TABLE IF NOT EXISTS vocabulary_books (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_code VARCHAR(50) NOT NULL UNIQUE COMMENT '词库标识：CET4/CET6/考研/GRE/IELTS/TOEFL/高考/专四/专八',
    book_name VARCHAR(100) NOT NULL COMMENT '词库名称',
    exam_type VARCHAR(30) COMMENT '考试类型',
    word_count INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS words (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    word VARCHAR(100) NOT NULL,
    phonetic VARCHAR(100),
    pos VARCHAR(50) COMMENT '词性',
    exam_type VARCHAR(30) COMMENT '考试类型',
    collins INT DEFAULT 0 COMMENT '柯林斯星级(1-5)',
    bnc_freq INT DEFAULT 0 COMMENT 'BNC词频排序',
    frq_freq INT DEFAULT 0 COMMENT '当代语料库词频排序',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_word_exam (word, exam_type),
    INDEX idx_word (word),
    INDEX idx_exam_type (exam_type)
);

CREATE TABLE IF NOT EXISTS word_sources (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    word_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    word_rank INT COMMENT '词库内排序',
    FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE,
    FOREIGN KEY (book_id) REFERENCES vocabulary_books(id),
    UNIQUE KEY uk_word_book (word_id, book_id)
);

CREATE TABLE IF NOT EXISTS meanings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    word_id BIGINT NOT NULL,
    meaning TEXT NOT NULL,
    part_of_speech VARCHAR(20),
    sort_order INT DEFAULT 0,
    FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS examples (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    word_id BIGINT NOT NULL,
    sentence TEXT NOT NULL,
    translation TEXT,
    source VARCHAR(50) COMMENT '来源：真题/教材/自行生成',
    FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS word_exam_points (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    word_id BIGINT NOT NULL,
    exam_type VARCHAR(20),
    year INT,
    section VARCHAR(50),
    point_text TEXT,
    example_sentence TEXT,
    FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS word_analysis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    word_id BIGINT NOT NULL,
    root_analysis TEXT COMMENT '词根词缀分析',
    word_family TEXT COMMENT '同根词族',
    FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE
);

-- -------------------------------------------
-- 3. 记忆状态与学习记录
-- -------------------------------------------

CREATE TABLE IF NOT EXISTS user_word_bind (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    word_id BIGINT NOT NULL,
    difficulty_initial DOUBLE,
    difficulty DOUBLE,
    stability DOUBLE,
    retrievability DOUBLE,
    next_review_time DATETIME,
    review_count INT DEFAULT 0,
    is_stubborn BOOLEAN DEFAULT FALSE,
    stubborn_since DATETIME,
    consecutive_errors INT DEFAULT 0,
    consecutive_correct_same_mode INT DEFAULT 0,
    last_study_mode VARCHAR(30),
    last_study_time DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_word (user_id, word_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (word_id) REFERENCES words(id),
    INDEX idx_next_review (user_id, next_review_time)
);

CREATE TABLE IF NOT EXISTS quiz_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    word_id BIGINT NOT NULL,
    study_mode VARCHAR(30) NOT NULL COMMENT 'quick_memory/context_deep/unified_review',
    grade DOUBLE COMMENT '最终传给FSRS的1.0~4.0浮点',
    composite_grade DOUBLE,
    step_results JSON COMMENT '各步骤详细得分',
    hint_total INT DEFAULT 0,
    reaction_time_ms INT,
    source VARCHAR(30) DEFAULT 'static' COMMENT 'static/dynamic/conversation',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_mode (user_id, study_mode),
    INDEX idx_user_word (user_id, word_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (word_id) REFERENCES words(id)
);

CREATE TABLE IF NOT EXISTS study_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    date DATE NOT NULL,
    review_count INT DEFAULT 0,
    new_word_count INT DEFAULT 0,
    estimated_minutes INT DEFAULT 0,
    intensity_label VARCHAR(20) COMMENT 'light/moderate/heavy',
    adoption_status VARCHAR(20) DEFAULT 'pending' COMMENT 'fully_accepted/modified/rejected/pending',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- -------------------------------------------
-- 4. 复习与顽固词
-- -------------------------------------------

CREATE TABLE IF NOT EXISTS forgetting_alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    word_id BIGINT NOT NULL,
    r_value DOUBLE,
    alert_date DATE,
    is_reviewed BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (word_id) REFERENCES words(id)
);

CREATE TABLE IF NOT EXISTS stubborn_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    word_id BIGINT NOT NULL,
    mark_time DATETIME,
    unmark_time DATETIME,
    reason VARCHAR(100),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (word_id) REFERENCES words(id)
);

-- -------------------------------------------
-- 5. Agent 对话相关
-- -------------------------------------------

CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_type VARCHAR(30) COMMENT 'general/exam_planning/word_query/diagnosis',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(10) NOT NULL COMMENT 'user/assistant/system',
    content TEXT,
    card_data JSON,
    suggested_actions JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS conversation_quiz_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    word_id BIGINT NOT NULL,
    session_id BIGINT,
    used_correctly BOOLEAN,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (word_id) REFERENCES words(id)
);

CREATE TABLE IF NOT EXISTS agent_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(30),
    request_params JSON,
    response TEXT,
    success BOOLEAN,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- -------------------------------------------
-- 6. 备考规划
-- -------------------------------------------

CREATE TABLE IF NOT EXISTS exam_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    exam_type VARCHAR(20),
    target_vocab INT,
    plan_json JSON,
    generated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
