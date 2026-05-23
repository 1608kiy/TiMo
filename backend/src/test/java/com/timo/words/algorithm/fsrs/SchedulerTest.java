package com.timo.words.algorithm.fsrs;

import com.timo.words.modules.study.entity.UserWordBind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SchedulerTest {

    private static final int WORD_LEN = 6; // baseline word length

    private UserWordBind bind;

    @BeforeEach
    void setUp() {
        bind = new UserWordBind();
        bind.setUserId(1L);
        bind.setWordId(100L);
        bind.setStability(Scheduler.INITIAL_STABILITY);
        bind.setDifficulty(Scheduler.INITIAL_DIFFICULTY);
        bind.setReviewCount(0);
        bind.setConsecutiveErrors(0);
        bind.setConsecutiveCorrectSameMode(0);
        bind.setLastStudyTime(LocalDateTime.now().minusDays(1));
    }

    @Test
    void review_perfectGrade_increasesStability() {
        ReviewResult result = Scheduler.review(
                bind, 4.0, "quick_memory",
                5.0, 0, 0, 0L, 8.0, 3.0, WORD_LEN);

        assertTrue(result.newStability() > Scheduler.INITIAL_STABILITY,
                "Stability should increase after perfect grade");
    }

    @Test
    void review_perfectGrade_nextReviewIsPositive() {
        ReviewResult result = Scheduler.review(
                bind, 4.0, "quick_memory",
                5.0, 0, 0, 0L, 8.0, 3.0, WORD_LEN);

        assertTrue(result.nextReviewDays() > 0,
                "Next review should be in the future");
    }

    @Test
    void review_perfectGrade_retrievabilityIs1() {
        ReviewResult result = Scheduler.review(
                bind, 4.0, "quick_memory",
                5.0, 0, 0, 0L, 8.0, 3.0, WORD_LEN);

        assertEquals(1.0, result.newRetrievability(), 0.001,
                "Post-review retrievability should be 1.0");
    }

    @Test
    void review_failureGrade_decreasesStability() {
        ReviewResult result = Scheduler.review(
                bind, 1.0, "quick_memory",
                10.0, 0, 0, 0L, 8.0, 3.0, WORD_LEN);

        assertTrue(result.newStability() < Scheduler.INITIAL_STABILITY,
                "Stability should decrease after failure grade");
    }

    @Test
    void review_failureGrade_increasesDifficulty() {
        ReviewResult result = Scheduler.review(
                bind, 1.0, "quick_memory",
                10.0, 0, 0, 0L, 8.0, 3.0, WORD_LEN);

        assertTrue(result.newDifficulty() > Scheduler.INITIAL_DIFFICULTY,
                "Difficulty should increase after failure");
    }

    @Test
    void review_stabilityWithinBounds() {
        ReviewResult result = Scheduler.review(
                bind, 4.0, "quick_memory",
                3.0, 0, 10, 10L, 8.0, 3.0, WORD_LEN);
        assertTrue(result.newStability() >= Scheduler.STABILITY_MIN);
        assertTrue(result.newStability() <= Scheduler.STABILITY_MAX);
    }

    @Test
    void review_stabilityClampedToMin() {
        bind.setStability(0.6);
        ReviewResult result = Scheduler.review(
                bind, 1.0, "quick_memory",
                15.0, 0, 0, 0L, 8.0, 3.0, WORD_LEN);

        assertTrue(result.newStability() >= Scheduler.STABILITY_MIN,
                "Stability should be clamped to min");
    }

    @Test
    void review_difficultyClamped() {
        ReviewResult result = Scheduler.review(
                bind, 1.0, "quick_memory",
                10.0, 0, 0, 0L, 8.0, 3.0, WORD_LEN);

        assertTrue(result.newDifficulty() >= Scheduler.DIFFICULTY_MIN);
        assertTrue(result.newDifficulty() <= Scheduler.DIFFICULTY_MAX);
    }

    @Test
    void review_dfIsComputed() {
        ReviewResult result = Scheduler.review(
                bind, 4.0, "quick_memory",
                5.0, 0, 0, 0L, 8.0, 3.0, WORD_LEN);

        assertTrue(result.df() > 0, "DF should be positive");
        assertTrue(result.df() <= 1.5, "DF should be within max bound");
    }

    @Test
    void review_higherGrade_higherNextReview() {
        ReviewResult good = Scheduler.review(
                bind, 4.0, "quick_memory",
                5.0, 0, 0, 0L, 8.0, 3.0, WORD_LEN);

        ReviewResult bad = Scheduler.review(
                bind, 2.0, "quick_memory",
                5.0, 0, 0, 0L, 8.0, 3.0, WORD_LEN);

        assertTrue(good.nextReviewDays() > bad.nextReviewDays(),
                "Higher grade should lead to longer next review interval");
    }

    @Test
    void review_grade4_growsFasterThanGrade3() {
        // New: grade differentiation in success branch.
        // Fresh bind with deltaT = 1 day so the formula has signal to work with.
        bind.setStability(2.0);
        bind.setLastStudyTime(LocalDateTime.now().minusDays(2));

        ReviewResult easy = Scheduler.review(
                bind, 4.0, "quick_memory",
                5.0, 0, 0, 0L, 8.0, 3.0, WORD_LEN);

        bind.setStability(2.0);
        bind.setLastStudyTime(LocalDateTime.now().minusDays(2));
        ReviewResult barely = Scheduler.review(
                bind, 3.0, "quick_memory",
                5.0, 0, 0, 0L, 8.0, 3.0, WORD_LEN);

        assertTrue(easy.newStability() > barely.newStability(),
                "grade=4.0 should produce larger S gain than grade=3.0 (1.3x vs 1.0x multiplier)");
    }
}
