package com.timo.words.modules.study.repository;

import com.timo.words.modules.study.entity.UserWordBind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserWordBindRepository extends JpaRepository<UserWordBind, Long> {

    Optional<UserWordBind> findByUserIdAndWordId(Long userId, Long wordId);

    List<UserWordBind> findByUserId(Long userId);

    List<UserWordBind> findByUserIdAndNextReviewTimeBefore(Long userId, LocalDateTime time);

    List<UserWordBind> findByUserIdAndIsStubbornTrue(Long userId);

    long countByUserId(Long userId);

    @Query("SELECT COUNT(b) FROM UserWordBind b WHERE b.userId = :userId AND b.masteredAt IS NOT NULL")
    long countMasteredByUserId(@Param("userId") Long userId);

    @Query("SELECT b FROM UserWordBind b WHERE b.userId = :userId AND b.nextReviewTime <= :time AND b.masteredAt IS NULL")
    List<UserWordBind> findActiveDueByUserIdAndTimeBefore(@Param("userId") Long userId, @Param("time") LocalDateTime time);

    @Query("SELECT COUNT(b) FROM UserWordBind b WHERE b.userId = :userId AND b.nextReviewTime <= :now")
    long countDueByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(b) FROM UserWordBind b WHERE b.userId = :userId AND b.isStubborn = true")
    long countStubbornByUserId(@Param("userId") Long userId);

    @Query("SELECT b.retrievability FROM UserWordBind b WHERE b.userId = :userId AND b.retrievability IS NOT NULL")
    List<Double> findRetrievabilitiesByUserId(@Param("userId") Long userId);

    @Query("SELECT b FROM UserWordBind b WHERE b.userId = :userId AND b.consecutiveErrors >= 1 ORDER BY b.consecutiveErrors DESC, b.difficulty DESC")
    List<UserWordBind> findWeakByUserId(@Param("userId") Long userId);

    @Query("SELECT b FROM UserWordBind b WHERE b.userId = :userId AND b.nextReviewTime > :now AND b.nextReviewTime <= :deadline ORDER BY b.nextReviewTime ASC")
    List<UserWordBind> findNearForgottenByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now, @Param("deadline") LocalDateTime deadline);

    @Query("SELECT b FROM UserWordBind b WHERE b.userId = :userId AND b.wordId IN :wordIds")
    List<UserWordBind> findByUserIdAndWordIdIn(@Param("userId") Long userId, @Param("wordIds") Collection<Long> wordIds);

    /**
     * Reverse-recall candidate pool: "half-learned" words best suited for active recall.
     *
     * Filters:
     *   - reviewCount >= 3       (the user has seen this word enough times to have a memory trace)
     *   - masteredAt IS NULL     (skip graduated words — they're already in long-term memory)
     *   - 1.0 ≤ stability ≤ 21.0 (mid-band — short stability = too fresh, > 21 = effectively mastered)
     *
     * Ordered by stability ascending so the "wobbliest" mid-band words are surfaced first.
     */
    @Query("SELECT b FROM UserWordBind b WHERE b.userId = :userId " +
            "AND b.reviewCount >= 3 AND b.masteredAt IS NULL " +
            "AND b.stability >= 1.0 AND b.stability <= 21.0 " +
            "ORDER BY b.stability ASC")
    List<UserWordBind> findReverseRecallCandidates(@Param("userId") Long userId,
                                                    org.springframework.data.domain.Pageable pageable);

    void deleteByWordIdIn(Collection<Long> wordIds);
}
