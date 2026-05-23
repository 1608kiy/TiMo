package com.timo.words.algorithm.fsrs;

import com.timo.words.modules.study.entity.UserWordBind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Multi-round lifecycle tests: trace a word through a realistic learning journey.
 * Verifies S/D/R invariants and behavioral correctness at each step.
 */
class SchedulerLifecycleTest {

    private static final int WORD_LEN = 6;

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
    void lifecycle_8Rounds_showsExpectedBehavior() {
        double[] grades = {1.0, 2.0, 3.0, 4.0, 4.0, 4.0, 2.0, 4.0};
        String[] labels = {
            "Round 1: complete failure",
            "Round 2: partial recognition",
            "Round 3: barely correct",
            "Round 4: perfect recall",
            "Round 5: perfect recall (consolidation)",
            "Round 6: perfect recall (consolidation)",
            "Round 7: forgot a bit",
            "Round 8: recovered"
        };

        System.out.println("=== Word Learning Lifecycle ===");
        System.out.printf("%-8s %-40s %6s %6s %6s %6s %6s%n",
                "Round", "Description", "Grade", "S", "D", "R", "nextR");
        System.out.println("-".repeat(85));

        double prevS = Scheduler.INITIAL_STABILITY;

        for (int i = 0; i < grades.length; i++) {
            ReviewResult result = Scheduler.review(
                    bind, grades[i], "quick_memory",
                    5.0, 0, i, (long) (i + 1),
                    8.0, 3.0, WORD_LEN);

            bind.setStability(result.newStability());
            bind.setDifficulty(result.newDifficulty());
            bind.setReviewCount(bind.getReviewCount() + 1);
            bind.setLastStudyTime(LocalDateTime.now());

            System.out.printf("  %-6d %-40s %5.1f  %5.3f  %5.3f  %5.3f  %5.1fd%n",
                    i + 1, labels[i], grades[i],
                    result.newStability(), result.newDifficulty(),
                    result.newRetrievability(), result.nextReviewDays());

            // S bounds
            assertTrue(result.newStability() >= Scheduler.STABILITY_MIN,
                    "Round %d: S=%f below min".formatted(i + 1, result.newStability()));
            assertTrue(result.newStability() <= Scheduler.STABILITY_MAX,
                    "Round %d: S=%f above max".formatted(i + 1, result.newStability()));

            // D bounds
            assertTrue(result.newDifficulty() >= Scheduler.DIFFICULTY_MIN);
            assertTrue(result.newDifficulty() <= Scheduler.DIFFICULTY_MAX);

            // R = 1.0 at review time
            assertEquals(1.0, result.newRetrievability(), 0.001);

            // nextReviewDays positive
            assertTrue(result.nextReviewDays() > 0);

            // DF in range
            assertTrue(result.df() >= 0.5 && result.df() <= 1.5,
                    "Round %d: DF=%f out of range".formatted(i + 1, result.df()));

            // Behavioral: good grades should not strictly decrease S
            if (grades[i] >= 3.0) {
                assertTrue(result.newStability() >= prevS - 0.01,
                        "Round %d: grade=%.1f but S decreased (%.3f → %.3f)"
                                .formatted(i + 1, grades[i], prevS, result.newStability()));
            }

            prevS = result.newStability();
        }

        System.out.println("=== All lifecycle checks passed ===");
    }

    // --- Invariant Stress Tests ---

    @Test
    void invariants_extremeReactionTime_fast() {
        ReviewResult result = Scheduler.review(
                bind, 4.0, "quick_memory",
                0.1, 0, 0, 0L, 8.0, 3.0, WORD_LEN);
        assertInBounds(result);
    }

    @Test
    void invariants_extremeReactionTime_slow() {
        ReviewResult result = Scheduler.review(
                bind, 1.0, "quick_memory",
                60.0, 0, 0, 0L, 8.0, 3.0, WORD_LEN);
        assertInBounds(result);
    }

    @Test
    void invariants_zeroHints() {
        ReviewResult result = Scheduler.review(
                bind, 3.0, "context_deep",
                5.0, 0, 0, 0L, 8.0, 3.0, WORD_LEN);
        assertInBounds(result);
    }

    @Test
    void invariants_maxHints() {
        ReviewResult result = Scheduler.review(
                bind, 1.0, "context_deep",
                5.0, 20, 0, 0L, 8.0, 3.0, WORD_LEN);
        assertInBounds(result);
    }

    @Test
    void invariants_highAccuracyHistory() {
        ReviewResult result = Scheduler.review(
                bind, 4.0, "quick_memory",
                5.0, 0, 100, 100L, 8.0, 3.0, WORD_LEN);
        assertInBounds(result);
    }

    @Test
    void invariants_lowAccuracyHistory() {
        ReviewResult result = Scheduler.review(
                bind, 1.0, "quick_memory",
                10.0, 0, 20, 100L, 8.0, 3.0, WORD_LEN);
        assertInBounds(result);
    }

    @Test
    void invariants_manyReviews() {
        for (int i = 0; i < 50; i++) {
            double grade = (i % 3 == 0) ? 2.0 : 4.0;
            ReviewResult result = Scheduler.review(
                    bind, grade, "quick_memory",
                    5.0, 0, i, (long) (i + 1),
                    8.0, 3.0, WORD_LEN);

            bind.setStability(result.newStability());
            bind.setDifficulty(result.newDifficulty());
            bind.setReviewCount(i + 1);
            bind.setLastStudyTime(LocalDateTime.now());

            assertInBounds(result);
        }
    }

    @Test
    void invariants_alwaysCorrect_thenAlwaysWrong() {
        for (int i = 0; i < 40; i++) {
            double grade = (i < 20) ? 4.0 : 1.0;
            ReviewResult result = Scheduler.review(
                    bind, grade, "quick_memory",
                    5.0, 0, i, (long) (i + 1),
                    8.0, 3.0, WORD_LEN);

            bind.setStability(result.newStability());
            bind.setDifficulty(result.newDifficulty());
            bind.setReviewCount(i + 1);
            bind.setLastStudyTime(LocalDateTime.now());

            assertInBounds(result);
        }
    }

    @Test
    void invariants_variableWordLength_neverViolatesBounds() {
        // Different word lengths from 3 to 20 chars
        int[] lengths = {3, 5, 6, 8, 12, 20};
        for (int len : lengths) {
            ReviewResult result = Scheduler.review(
                    bind, 3.5, "quick_memory",
                    5.0, 0, 5, 10L, 8.0, 3.0, len);
            assertInBounds(result);
        }
    }

    private void assertInBounds(ReviewResult result) {
        assertTrue(result.newStability() >= Scheduler.STABILITY_MIN
                && result.newStability() <= Scheduler.STABILITY_MAX,
                "S out of range: " + result.newStability());
        assertTrue(result.newDifficulty() >= Scheduler.DIFFICULTY_MIN
                && result.newDifficulty() <= Scheduler.DIFFICULTY_MAX,
                "D out of range: " + result.newDifficulty());
        assertTrue(result.newRetrievability() >= 0.0
                && result.newRetrievability() <= 1.0,
                "R out of range: " + result.newRetrievability());
        assertTrue(result.df() >= 0.5 && result.df() <= 1.5,
                "DF out of range: " + result.df());
    }
}
