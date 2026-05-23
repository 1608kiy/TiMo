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

    // Beta-Bernoulli smoothing prior for λ_acc — solves the cold-start problem
    // where a single correct answer would otherwise yield λ_acc = 1.1 (overconfident).
    public static final double ACC_PRIOR_STRENGTH = 5.0;
    public static final double ACC_PRIOR_MEAN = 0.7;

    // Baseline word length for RT normalization (typical English exam word ~6 chars).
    // Used to adjust personal μ/σ when comparing reaction time on different-length words.
    public static final double BASELINE_WORD_LENGTH = 6.0;
    public static final int MIN_WORD_LENGTH = 3;

    public static final double LAMBDA_MIN = 0.7;
    public static final double LAMBDA_MAX = 1.3;
    public static final double DF_MIN = 0.5;
    public static final double DF_MAX = 1.5;

    public static final String QUICK_MEMORY = "quick_memory";
    public static final String CONTEXT_DEEP = "context_deep";
    public static final String UNIFIED_REVIEW = "unified_review";
    public static final String REVERSE_RECALL = "reverse_recall";

    private DynamicForgettingFactor() {}

    /**
     * Compute the Dynamic Forgetting Factor.
     *
     * @param reviewCount        # of times this word has been reviewed
     * @param correctCount       # of correct (grade ≥ 3.0) reviews
     * @param totalAttempts      # of total review attempts (denominator for accuracy)
     * @param userMu             user's personal RT mean baseline (seconds)
     * @param userSigma          user's personal RT std dev baseline (seconds)
     * @param studyMode          quick_memory / context_deep / unified_review / reverse_recall
     * @param reactionTimeSec    this submission's raw reaction time in seconds
     * @param hintTotal          # of hints used (context_deep only)
     * @param wordLength         length in chars of the target word (≥ MIN_WORD_LENGTH)
     */
    public static double calculate(int reviewCount, int correctCount, long totalAttempts,
                                   double userMu, double userSigma,
                                   String studyMode,
                                   double reactionTimeSec, int hintTotal,
                                   int wordLength) {

        double mu = correctCount < COLD_START_THRESHOLD ? COLD_START_MU : userMu;
        double sigma = correctCount < COLD_START_THRESHOLD ? COLD_START_SIGMA : userSigma;

        double lambdaRt = lambdaRt(reactionTimeSec, mu, sigma, wordLength);
        double lambdaAcc = lambdaAcc(correctCount, totalAttempts);
        double lambdaSkip = lambdaSkipCustom(hintTotal, reviewCount);

        double df;
        switch (studyMode) {
            case QUICK_MEMORY:
            case UNIFIED_REVIEW:
            case REVERSE_RECALL:
                df = lambdaRt * lambdaAcc;
                break;
            case CONTEXT_DEEP:
                df = lambdaAcc * lambdaSkip;
                break;
            default:
                df = 1.0;
        }

        return ClampUtil.clamp(df, DF_MIN, DF_MAX);
    }

    /**
     * λ_rt with word-length normalization.
     * Longer words objectively take longer to read; adjust μ and σ by √(L/6) so that
     * "fast for a 12-char word" is not falsely judged as "slow for any word".
     */
    public static double lambdaRt(double t, double mu, double sigma, int wordLength) {
        if (sigma <= 0) sigma = 3.0;
        double scale = Math.sqrt(Math.max(wordLength, MIN_WORD_LENGTH) / BASELINE_WORD_LENGTH);
        double adjMu = mu * scale;
        double adjSigma = sigma * scale;
        double raw = 1.0 + THETA_RT * (1.0 - (t - adjMu) / (2.0 * adjSigma));
        return ClampUtil.clamp(raw, LAMBDA_MIN, LAMBDA_MAX);
    }

    /**
     * λ_acc with Beta-Bernoulli smoothing.
     *
     *   smoothedRate = (correctCount + α × priorMean) / (totalAttempts + α)
     *
     * With α=5, priorMean=0.7:
     *   - 0 attempts:   smoothed = 0.7  → λ_acc ≈ 0.95 (slight conservatism for new words)
     *   - 1/1 correct:  smoothed = 0.75 → λ_acc ≈ 0.975
     *   - 50/50:        smoothed ≈ 0.95 → λ_acc ≈ 1.075
     *
     * Replaces the old binary "if count==0 return 1.0 else compute" branch which gave
     * misleading confidence to early samples.
     */
    public static double lambdaAcc(int correctCount, long totalAttempts) {
        double smoothedRate = (correctCount + ACC_PRIOR_STRENGTH * ACC_PRIOR_MEAN)
                / (totalAttempts + ACC_PRIOR_STRENGTH);
        double raw = 1.0 + THETA_ACC * (smoothedRate - 0.8);
        return ClampUtil.clamp(raw, LAMBDA_MIN, LAMBDA_MAX);
    }

    public static double lambdaSkipCustom(int hintTotal, int reviewCount) {
        double raw = 1.0 - THETA_SKIP * (hintTotal / 5.0) * (1.0 + GAMMA * reviewCount);
        return ClampUtil.clamp(raw, LAMBDA_MIN, LAMBDA_MAX);
    }
}
