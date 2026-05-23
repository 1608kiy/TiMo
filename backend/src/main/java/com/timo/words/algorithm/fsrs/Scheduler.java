package com.timo.words.algorithm.fsrs;

import com.timo.words.algorithm.common.ClampUtil;
import com.timo.words.algorithm.df.DynamicForgettingFactor;
import com.timo.words.modules.study.entity.UserWordBind;

import java.time.Duration;
import java.time.LocalDateTime;

public final class Scheduler {

    public static final double INITIAL_STABILITY = 1.0;
    public static final double INITIAL_DIFFICULTY = 5.0;
    // Stability bounds: lower bound prevents division by zero (~2.4h floor);
    // upper bound is effectively unbounded so the spaced-repetition interval can grow
    // as the user demonstrates retention. The earlier [0.5, 1.5] range was a DF clamp
    // mistakenly applied to S_final, capping every next-review at ≤1.5 days.
    public static final double STABILITY_MIN = 0.1;
    public static final double STABILITY_MAX = 36500.0;
    public static final double DIFFICULTY_MIN = 1.0;
    public static final double DIFFICULTY_MAX = 10.0;
    public static final double R_THRESHOLD = 0.9;

    private Scheduler() {}

    /**
     * Standard FSRS update with DF and word-length awareness.
     *
     * @param bind              the user-word binding (current S/D/lastStudyTime)
     * @param grade             1.0–4.0 (with 0.5 steps for fine-grained quick_memory)
     * @param studyMode         quick_memory / context_deep / unified_review / reverse_recall
     * @param reactionTimeSec   raw seconds
     * @param hintTotal         hints used (context_deep only)
     * @param correctCount      historical # of grade≥3 reviews on this word
     * @param totalAttempts     historical # of all reviews on this word
     * @param userMu/userSigma  user's personal RT baseline
     * @param wordLength        target word's char length (for RT normalization)
     */
    public static ReviewResult review(UserWordBind bind, double grade, String studyMode,
                                      double reactionTimeSec, int hintTotal,
                                      int correctCount, long totalAttempts,
                                      double userMu, double userSigma,
                                      int wordLength) {

        // 1. Compute delta_t (days since last review)
        double deltaT = computeDeltaT(bind);

        // 2. Compute current retrievability
        double stability = bind.getStability() != null ? bind.getStability() : INITIAL_STABILITY;
        double currentR = Math.exp(Math.log(R_THRESHOLD) * deltaT / stability);

        // 3. Standard FSRS difficulty update
        double difficulty = bind.getDifficulty() != null ? bind.getDifficulty() : INITIAL_DIFFICULTY;
        double dNew = updateDifficulty(difficulty, grade);
        dNew = ClampUtil.clamp(dNew, DIFFICULTY_MIN, DIFFICULTY_MAX);

        // 4. Standard FSRS stability update (grade-aware in success branch)
        double sPrime = updateStability(stability, dNew, grade, currentR);

        // 5. Compute DF
        int reviewCount = bind.getReviewCount() != null ? bind.getReviewCount() : 0;
        double df = DynamicForgettingFactor.calculate(
                reviewCount, correctCount, totalAttempts,
                userMu, userSigma, studyMode,
                reactionTimeSec, hintTotal, wordLength);

        // 6. Apply DF and clamp (S_final not artificially upper-bounded)
        double sFinal = ClampUtil.clamp(sPrime * df, STABILITY_MIN, STABILITY_MAX);

        // 7. Compute next review days
        double nextReviewDays = computeNextReviewDays(sFinal);

        // 8. Compute post-review retrievability (delta_t = 0)
        double newR = 1.0;

        return new ReviewResult(
                sFinal, dNew, newR, nextReviewDays, df,
                false, false
        );
    }

    private static double computeDeltaT(UserWordBind bind) {
        LocalDateTime lastStudy = bind.getLastStudyTime();
        if (lastStudy == null) {
            lastStudy = bind.getCreatedAt();
        }
        if (lastStudy == null) {
            return 0.0;
        }
        long millis = Duration.between(lastStudy, LocalDateTime.now()).toMillis();
        return Math.max(0, millis / (1000.0 * 60 * 60 * 24));
    }

    private static double updateDifficulty(double d, double grade) {
        return d - 0.1 + (4.0 - grade) * (0.1 + (4.0 - grade) * 0.02);
    }

    /**
     * Stability update with grade differentiation in the success branch.
     * Grade ≥ 3.0 = success; the (grade - 3.0) delta scales the stability gain:
     *   grade 3.0 → 1.0x base factor (minimal gain — "barely correct")
     *   grade 3.5 → 1.15x          ("mid confidence")
     *   grade 4.0 → 1.3x           ("fluent recall — earned a bigger interval")
     *
     * This lets the new 5-tier QuickMemory grading actually move the needle differently;
     * previously 3.0 and 4.0 produced identical S because grade value wasn't used inside
     * the success-branch formula.
     */
    private static double updateStability(double s, double d, double grade, double r) {
        if (grade >= 3.0) {
            double baseFactor = Math.exp(0.1) * (11.0 - d) * Math.pow(s, -0.2)
                    * (Math.exp(0.05 * (1.0 - r)) - 1.0);
            double gradeMultiplier = 1.0 + (grade - 3.0) * 0.3;
            return s * (1.0 + baseFactor * gradeMultiplier);
        } else {
            // Failure branch (standard FSRS formula)
            return Math.pow(d, -0.2)
                    * (Math.exp(0.05 * (1.0 - r)) - 1.0)
                    * Math.pow(s, 0.1);
        }
    }

    private static double computeNextReviewDays(double sFinal) {
        // Next review when R drops to threshold (R = 0.9 at t = S)
        return sFinal;
    }
}
