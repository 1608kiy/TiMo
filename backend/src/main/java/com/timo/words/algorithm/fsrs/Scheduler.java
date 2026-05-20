package com.timo.words.algorithm.fsrs;

import com.timo.words.algorithm.common.ClampUtil;
import com.timo.words.algorithm.df.DynamicForgettingFactor;
import com.timo.words.modules.study.entity.UserWordBind;

import java.time.Duration;
import java.time.LocalDateTime;

public final class Scheduler {

    public static final double INITIAL_STABILITY = 1.0;
    public static final double INITIAL_DIFFICULTY = 5.0;
    public static final double STABILITY_MIN = 0.5;
    public static final double STABILITY_MAX = 1.5;
    public static final double DIFFICULTY_MIN = 1.0;
    public static final double DIFFICULTY_MAX = 10.0;
    public static final double R_THRESHOLD = 0.9;

    private Scheduler() {}

    public static ReviewResult review(UserWordBind bind, double grade, String studyMode,
                                      double reactionTimeSeconds, int hintTotal,
                                      int correctCount, double historicalCorrectRate,
                                      double userMu, double userSigma) {

        // 1. Compute delta_t (days since last review)
        double deltaT = computeDeltaT(bind);

        // 2. Compute current retrievability
        double stability = bind.getStability() != null ? bind.getStability() : INITIAL_STABILITY;
        double currentR = Math.exp(Math.log(R_THRESHOLD) * deltaT / stability);

        // 3. Standard FSRS difficulty update
        double difficulty = bind.getDifficulty() != null ? bind.getDifficulty() : INITIAL_DIFFICULTY;
        double dNew = updateDifficulty(difficulty, grade);
        dNew = ClampUtil.clamp(dNew, DIFFICULTY_MIN, DIFFICULTY_MAX);

        // 4. Standard FSRS stability update
        double sPrime = updateStability(stability, dNew, grade, currentR);

        // 5. Compute DF
        int reviewCount = bind.getReviewCount() != null ? bind.getReviewCount() : 0;
        double df = DynamicForgettingFactor.calculate(
                reviewCount, correctCount, historicalCorrectRate,
                userMu, userSigma, studyMode,
                reactionTimeSeconds, hintTotal);

        // 6. Apply DF and clamp
        double sFinal = ClampUtil.clamp(sPrime * df, STABILITY_MIN, STABILITY_MAX);

        // 7. Compute next review days
        double nextReviewDays = computeNextReviewDays(sFinal);

        // 8. Compute post-review retrievability (delta_t = 0)
        double newR = 1.0; // At review time, R = 1.0

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

    private static double updateStability(double s, double d, double grade, double r) {
        if (grade >= 3.0) {
            // Success branch
            double factor = Math.exp(0.1) * (11.0 - d) * Math.pow(s, -0.2)
                    * (Math.exp(0.05 * (1.0 - r)) - 1.0);
            return s * (1.0 + factor);
        } else {
            // Failure branch (standard FSRS formula)
            return Math.pow(d, -0.2)
                    * (Math.exp(0.05 * (1.0 - r)) - 1.0)
                    * Math.pow(s, 0.1);
        }
    }

    private static double computeNextReviewDays(double sFinal) {
        // Next review when R drops to threshold
        // R = exp(ln(0.9) * t / S) = 0.9 when t = S
        return sFinal;
    }
}
