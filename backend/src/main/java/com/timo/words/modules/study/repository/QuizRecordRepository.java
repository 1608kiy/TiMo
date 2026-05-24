package com.timo.words.modules.study.repository;

import com.timo.words.modules.study.entity.QuizRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface QuizRecordRepository extends JpaRepository<QuizRecord, Long> {

    long countByUserIdAndWordId(Long userId, Long wordId);

    long countByUserIdAndWordIdAndGradeGreaterThanEqual(Long userId, Long wordId, double grade);

    @Query("SELECT q FROM QuizRecord q WHERE q.userId = :userId AND q.wordId = :wordId ORDER BY q.createdAt DESC")
    List<QuizRecord> findRecentByUserIdAndWordId(@Param("userId") Long userId, @Param("wordId") Long wordId, org.springframework.data.domain.Pageable pageable);

    long countByUserId(Long userId);

    long countByUserIdAndGradeGreaterThanEqual(Long userId, double grade);

    // Daily quota — used by ExamPlanService.getTodayQuota (Spring Data derived queries)
    long countByUserIdAndCreatedAtAfterAndStudyMode(Long userId, LocalDateTime since, String studyMode);

    long countByUserIdAndCreatedAtAfterAndStudyModeIn(Long userId, LocalDateTime since, Collection<String> studyModes);

    @Query("SELECT COUNT(q) FROM QuizRecord q WHERE q.userId = :userId AND q.createdAt >= :since AND q.studyMode = :studyMode")
    long countByUserIdSinceAndStudyMode(@Param("userId") Long userId,
                                         @Param("since") LocalDateTime since,
                                         @Param("studyMode") String studyMode);

    @Query("SELECT COUNT(DISTINCT q.wordId) FROM QuizRecord q WHERE q.userId = :userId AND q.createdAt >= :since AND q.studyMode = :studyMode")
    long countDistinctWordIdByUserIdSinceAndStudyMode(@Param("userId") Long userId,
                                                       @Param("since") LocalDateTime since,
                                                       @Param("studyMode") String studyMode);

    @Query("SELECT COUNT(q) FROM QuizRecord q WHERE q.userId = :userId AND q.createdAt >= :since")
    long countByUserIdSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(DISTINCT q.wordId) FROM QuizRecord q WHERE q.userId = :userId")
    long countDistinctWordIdByUserId(@Param("userId") Long userId);

    // Batch queries for N+1 optimization
    @Query("SELECT q.wordId, COUNT(q) FROM QuizRecord q WHERE q.userId = :userId AND q.wordId IN :wordIds GROUP BY q.wordId")
    List<Object[]> countByUserIdAndWordIdIn(@Param("userId") Long userId, @Param("wordIds") Collection<Long> wordIds);

    @Query("SELECT q.wordId, COUNT(q) FROM QuizRecord q WHERE q.userId = :userId AND q.wordId IN :wordIds AND q.grade >= :grade GROUP BY q.wordId")
    List<Object[]> countByUserIdAndWordIdInAndGradeGte(@Param("userId") Long userId, @Param("wordIds") Collection<Long> wordIds, @Param("grade") double grade);

    @Query("SELECT AVG(q.grade) FROM QuizRecord q WHERE q.userId = :userId")
    Double avgGradeByUserId(@Param("userId") Long userId);

    @Query("SELECT AVG(q.grade) FROM QuizRecord q WHERE q.userId = :userId AND q.createdAt >= :since")
    Double avgGradeByUserIdSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    List<QuizRecord> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT q.reactionTimeMs FROM QuizRecord q WHERE q.userId = :userId AND q.reactionTimeMs IS NOT NULL AND q.createdAt >= :since")
    List<Integer> findReactionTimesSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Query("SELECT q.studyMode, COUNT(q) FROM QuizRecord q WHERE q.userId = :userId AND q.createdAt >= :since GROUP BY q.studyMode")
    List<Object[]> countByStudyModeSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Query("SELECT FUNCTION('DATE', q.createdAt) as day, COUNT(q) FROM QuizRecord q WHERE q.userId = :userId AND q.createdAt >= :since GROUP BY FUNCTION('DATE', q.createdAt) ORDER BY day")
    List<Object[]> countDailyByUserIdSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(DISTINCT FUNCTION('DATE', q.createdAt)) FROM QuizRecord q WHERE q.userId = :userId AND q.createdAt >= :since")
    long countStudyDaysSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(DISTINCT q.wordId) FROM QuizRecord q WHERE q.userId = :userId AND q.createdAt >= :since")
    long countDistinctWordIdByUserIdSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Query("SELECT q.studyMode, COUNT(q) FROM QuizRecord q GROUP BY q.studyMode")
    List<Object[]> countByStudyModeGlobal();

    void deleteByWordIdIn(Collection<Long> wordIds);

    @Query(value = "SELECT DISTINCT DATE(created_at) FROM quiz_records WHERE user_id = :userId ORDER BY 1 DESC", nativeQuery = true)
    List<java.sql.Date> findDistinctStudyDatesByUserId(@Param("userId") Long userId);
}
