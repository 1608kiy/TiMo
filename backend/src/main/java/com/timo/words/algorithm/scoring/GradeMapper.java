package com.timo.words.algorithm.scoring;

import com.timo.words.algorithm.common.ClampUtil;

public final class GradeMapper {

    public static final double GRADE_MIN = 1.0;
    public static final double GRADE_MAX = 4.0;

    private GradeMapper() {}

    /**
     * Quick Memory mode: discrete mapping.
     * recognized + verifiedCorrect → 4.0
     * recognized + wrong → 2.0
     * not recognized → 1.0
     */
    public static double mapQuickMemory(boolean recognized, boolean verifiedCorrect) {
        if (!recognized) return 1.0;
        return verifiedCorrect ? 4.0 : 2.0;
    }

    /**
     * Context Deep mode: weighted composite of steps 2-5.
     * s2-s5 each in range [0, 4].
     */
    public static double mapContextDeep(int s2, int s3, int s4, int s5) {
        double composite = 1.0 + 3.0 * (
                0.20 * s2 / 4.0 +
                0.25 * s3 / 4.0 +
                0.30 * s4 / 4.0 +
                0.25 * s5 / 4.0
        );
        double penalty = 1.0 - 0.2 * (1.0 - s5 / 4.0);
        double result = composite * penalty;
        return ClampUtil.clamp(result, GRADE_MIN, GRADE_MAX);
    }

    /**
     * Unified Review mode: three-step weighted composite.
     * step1-step3 each in range [0, 5].
     */
    public static double mapUnifiedReview(int step1, int step2, int step3) {
        double grade = 1.0 + 3.0 * (
                0.35 * step1 / 5.0 +
                0.30 * step2 / 5.0 +
                0.35 * step3 / 5.0
        );
        return ClampUtil.clamp(grade, GRADE_MIN, GRADE_MAX);
    }
}
