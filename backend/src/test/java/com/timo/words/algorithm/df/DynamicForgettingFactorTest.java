package com.timo.words.algorithm.df;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DynamicForgettingFactorTest {

    // --- lambdaRt Tests (now with wordLength) ---

    @Test
    void lambdaRt_atMean_baselineLength_returns1_3() {
        // wordLength = 6 (baseline) → scale = 1, so adjMu=mu, adjSigma=sigma
        // t=8, mu=8 → (t-mu)/(2σ)=0 → raw = 1 + 0.3*(1-0) = 1.3 (clamped to max)
        assertEquals(1.3, DynamicForgettingFactor.lambdaRt(8.0, 8.0, 3.0, 6), 0.001);
    }

    @Test
    void lambdaRt_slowReaction_returnsLower() {
        // baseline length, t=12, mu=8, σ=3 → (12-8)/(2*3)=0.667 → raw=1+0.3*(1-0.667)=1.1
        double result = DynamicForgettingFactor.lambdaRt(12.0, 8.0, 3.0, 6);
        assertEquals(1.1, result, 0.001);
    }

    @Test
    void lambdaRt_verySlow_clampedToMin() {
        // baseline length, t=20, mu=8, σ=3 → (20-8)/6=2.0 → raw=1+0.3*(-1)=0.7 (clamp)
        double result = DynamicForgettingFactor.lambdaRt(20.0, 8.0, 3.0, 6);
        assertEquals(0.7, result, 0.001);
    }

    @Test
    void lambdaRt_veryFast_clampedToMax() {
        double result = DynamicForgettingFactor.lambdaRt(0.0, 8.0, 3.0, 6);
        assertEquals(1.3, result, 0.001);
    }

    @Test
    void lambdaRt_longWord_grantsMoreTime() {
        // 12-char word at t=12s should NOT be penalized — scale = √(12/6) ≈ 1.414
        // adjMu = 8 * 1.414 = 11.31; (12-11.31)/(2*3*1.414) ≈ 0.081
        // raw = 1 + 0.3*(1-0.081) ≈ 1.276
        double resultLong = DynamicForgettingFactor.lambdaRt(12.0, 8.0, 3.0, 12);
        double resultShort = DynamicForgettingFactor.lambdaRt(12.0, 8.0, 3.0, 6);
        assertTrue(resultLong > resultShort,
                "Long word should get higher λ_rt than short word at same RT");
    }

    @Test
    void lambdaRt_shortWord_appliesStricterStandard() {
        // 3-char word at t=8s — for "cat", 8s is genuinely slow
        // scale = √(3/6) = √0.5 ≈ 0.707; adjMu = 8 * 0.707 ≈ 5.66
        // (8-5.66)/(2*3*0.707) ≈ 0.55; raw = 1 + 0.3*(1-0.55) = 1.135
        double resultShort = DynamicForgettingFactor.lambdaRt(8.0, 8.0, 3.0, 3);
        double resultBaseline = DynamicForgettingFactor.lambdaRt(8.0, 8.0, 3.0, 6);
        assertTrue(resultShort < resultBaseline,
                "Short word should get lower λ_rt than baseline at same RT (slowness penalized)");
    }

    @Test
    void lambdaRt_belowMinWordLength_clampedToMin() {
        // 1-char and 3-char should produce the same result (MIN_WORD_LENGTH = 3)
        double r1 = DynamicForgettingFactor.lambdaRt(8.0, 8.0, 3.0, 1);
        double r3 = DynamicForgettingFactor.lambdaRt(8.0, 8.0, 3.0, 3);
        assertEquals(r1, r3, 0.001);
    }

    // --- lambdaAcc Tests (Beta-Bernoulli smoothing) ---

    @Test
    void lambdaAcc_zeroAttempts_returnsConservative() {
        // smoothedRate = (0 + 5*0.7) / (0 + 5) = 0.7 → raw = 1 + 0.5*(0.7-0.8) = 0.95
        assertEquals(0.95, DynamicForgettingFactor.lambdaAcc(0, 0), 0.001);
    }

    @Test
    void lambdaAcc_oneCorrectOutOfOne_stillConservative() {
        // smoothedRate = (1 + 3.5) / (1 + 5) = 0.75 → raw = 1 + 0.5*(0.75-0.8) = 0.975
        // Old behavior would have given λ_acc=1.1 here — overconfident on 1 sample.
        double result = DynamicForgettingFactor.lambdaAcc(1, 1);
        assertEquals(0.975, result, 0.001);
        assertTrue(result < 1.0, "Single correct attempt should still be conservative");
    }

    @Test
    void lambdaAcc_perfectLargeSample_higherThanSmallSample() {
        double small = DynamicForgettingFactor.lambdaAcc(1, 1);
        double large = DynamicForgettingFactor.lambdaAcc(50, 50);
        assertTrue(large > small, "More evidence of perfect accuracy should yield higher λ_acc");
    }

    @Test
    void lambdaAcc_largeSamplePerfect_approachesMax() {
        // 50 correct / 50 attempts: smoothed = (50 + 3.5)/(50 + 5) = 53.5/55 ≈ 0.973
        // raw = 1 + 0.5*(0.973-0.8) ≈ 1.086
        double result = DynamicForgettingFactor.lambdaAcc(50, 50);
        assertEquals(1.086, result, 0.01);
    }

    @Test
    void lambdaAcc_lowAccuracyLargeSample_clampedToMin() {
        // 0 / 50: smoothed = 3.5 / 55 ≈ 0.064 → raw = 1 + 0.5*(-0.736) = 0.632 → clamp 0.7
        double result = DynamicForgettingFactor.lambdaAcc(0, 50);
        assertEquals(0.7, result, 0.001);
    }

    // --- lambdaSkipCustom Tests (unchanged) ---

    @Test
    void lambdaSkip_noHints_returns1() {
        assertEquals(1.0, DynamicForgettingFactor.lambdaSkipCustom(0, 0), 0.001);
    }

    @Test
    void lambdaSkip_manyHints_lowers() {
        assertEquals(0.8, DynamicForgettingFactor.lambdaSkipCustom(5, 0), 0.001);
    }

    @Test
    void lambdaSkip_manyHintsAndReviews_clampedToMin() {
        assertEquals(0.7, DynamicForgettingFactor.lambdaSkipCustom(5, 5), 0.001);
    }

    // --- DF Composition Tests (new signature) ---

    @Test
    void df_quickMemory_usesLambdaRtAndLambdaAcc() {
        // Cold start (correctCount=0), t=8s, mu=8, σ=3, wordLength=6 → λ_rt=1.3
        // lambdaAcc(0, 0) = 0.95 → DF = 1.3 * 0.95 = 1.235
        double df = DynamicForgettingFactor.calculate(
                0, 0, 0L, 8.0, 3.0, "quick_memory", 8.0, 0, 6);
        assertEquals(1.235, df, 0.001);
    }

    @Test
    void df_contextDeep_usesLambdaAccAndLambdaSkip() {
        // correctCount=0, totalAttempts=0 → λ_acc=0.95
        // hintTotal=0 → λ_skip=1.0
        // DF = 0.95
        double df = DynamicForgettingFactor.calculate(
                0, 0, 0L, 8.0, 3.0, "context_deep", 8.0, 0, 6);
        assertEquals(0.95, df, 0.001);
    }

    @Test
    void df_unifiedReview_usesLambdaRtAndLambdaAcc() {
        double df = DynamicForgettingFactor.calculate(
                0, 0, 0L, 8.0, 3.0, "unified_review", 8.0, 0, 6);
        assertEquals(1.235, df, 0.001);
    }

    @Test
    void df_reverseRecall_usesLambdaRtAndLambdaAcc() {
        // reverse_recall behaves like quick_memory / unified_review for DF composition
        double df = DynamicForgettingFactor.calculate(
                0, 0, 0L, 8.0, 3.0, "reverse_recall", 8.0, 0, 6);
        assertEquals(1.235, df, 0.001);
    }

    @Test
    void df_unknownMode_returns1() {
        double df = DynamicForgettingFactor.calculate(
                0, 0, 0L, 8.0, 3.0, "unknown", 8.0, 0, 6);
        assertEquals(1.0, df, 0.001);
    }

    @Test
    void df_clampedToRange() {
        // Extreme fast reaction + high accuracy + no hints — should stay within DF_MIN..DF_MAX
        double df = DynamicForgettingFactor.calculate(
                0, 50, 50L, 8.0, 3.0, "quick_memory", 2.0, 0, 6);
        assertTrue(df >= DynamicForgettingFactor.DF_MIN);
        assertTrue(df <= DynamicForgettingFactor.DF_MAX);
    }
}
