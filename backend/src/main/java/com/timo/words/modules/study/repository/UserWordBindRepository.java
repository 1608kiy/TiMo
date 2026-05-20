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

    @Query("SELECT COUNT(b) FROM UserWordBind b WHERE b.userId = :userId AND b.stability >= 1.2")
    long countMasteredByUserId(@Param("userId") Long userId);

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

    void deleteByWordIdIn(Collection<Long> wordIds);
}
