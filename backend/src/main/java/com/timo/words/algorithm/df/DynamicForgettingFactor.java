package com.timo.words.algorithm.df;

import com.timo.words.algorithm.common.ClampUtil;

public final class DynamicForgettingFactor {

    public static final double THETA_RT = 0.3;
    public static final double THETA_ACC = 0.5;
    public static final double THETA_SKIP = 0.2;
    public static final double GAMMA = 0.1;

    public static final double COLD_START_MU = 8.0;
    public static final double COLD_START_SIGMA = 3.0;
    public static final int COLD_START_THRESHOLD = 30;

    public static final double LAMBDA_MIN = 0.7;
    public static final double LAMBDA_MAX = 1.3;
    public static final double DF_MIN = 0.5;
    public static final double DF_MAX = 1.5;

    public static final String QUICK_MEMORY = "quick_memory";
    public static final String CONTEXT_DEEP = "context_deep";
    public static final String UNIFIED_REVIEW = "unified_review";

    private DynamicForgettingFactor() {}

    public static double calculate(int reviewCount, int correctCount,
                                   double historicalCorrectRate,
                                   double userMu, double userSigma,
                                   String studyMode,
                                   double reactionTimeSeconds, int hintTotal) {

        double mu = correctCount < COLD_START_THRESHOLD ? COLD_START_MU : userMu;
        double sigma = correctCount < COLD_START_THRESHOLD ? COLD_START_SIGMA : userSigma;

        double lambdaRt = lambdaRt(reactionTimeSeconds, mu, sigma);
        double lambdaAcc = lambdaAcc(historicalCorrectRate, correctCount);
        double lambdaSkip = lambdaSkipCustom(hintTotal, reviewCount);

        double df;
        switch (studyMode) {
            case QUICK_MEMORY:
                df = lambdaRt * lambdaAcc;
                break;
            case CONTEXT_DEEP:
                df = lambdaAcc * lambdaSkip;
                break;
            case UNIFIED_REVIEW:
                df = lambdaRt * lambdaAcc;
                break;
            default:
                df = 1.0;
        }

        return ClampUtil.clamp(df, DF_MIN, DF_MAX);
    }

    public static double lambdaRt(double t, double mu, double sigma) {
        if (sigma <= 0) sigma = 3.0;
        double raw = 1.0 + THETA_RT * (1.0 - (t - mu) / (2.0 * sigma));
        return ClampUtil.clamp(raw, LAMBDA_MIN, LAMBDA_MAX);
    }

    public static double lambdaAcc(double p, int correctCount) {
        if (correctCount == 0) return 1.0;
        double raw = 1.0 + THETA_ACC * (p - 0.8);
        return ClampUtil.clamp(raw, LAMBDA_MIN, LAMBDA_MAX);
    }

    public static double lambdaSkipCustom(int hintTotal, int reviewCount) {
        double raw = 1.0 - THETA_SKIP * (hintTotal / 5.0) * (1.0 + GAMMA * reviewCount);
        return ClampUtil.clamp(raw, LAMBDA_MIN, LAMBDA_MAX);
    }
}
