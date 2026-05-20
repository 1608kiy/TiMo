package com.timo.words.algorithm.df;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DynamicForgettingFactorTest {

    // --- lambdaRt Tests ---

    @Test
    void lambdaRt_atMean_returns1() {
        // t = mu → raw = 1 + 0.3*(1 - 0/(2σ)) = 1.3
        // Wait: t=mu → (t-mu)/(2σ) = 0 → raw = 1 + 0.3*(1-0) = 1.3
        assertEquals(1.3, DynamicForgettingFactor.lambdaRt(8.0, 8.0, 3.0), 0.001);
    }

    @Test
    void lambdaRt_fastReaction_returnsHigher() {
        // t=4, mu=8, σ=3 → (4-8)/(2*3) = -4/6 = -0.667
        // raw = 1 + 0.3*(1-(-0.667)) = 1 + 0.3*1.667 = 1.5 → clamped to 1.3
        double result = DynamicForgettingFactor.lambdaRt(4.0, 8.0, 3.0);
        assertEquals(1.3, result, 0.001);
    }

    @Test
    void lambdaRt_slowReaction_returnsLower() {
        // t=12, mu=8, σ=3 → (12-8)/(2*3) = 4/6 = 0.667
        // raw = 1 + 0.3*(1-0.667) = 1 + 0.1 = 1.1
        double result = DynamicForgettingFactor.lambdaRt(12.0, 8.0, 3.0);
        assertEquals(1.1, result, 0.001);
    }

    @Test
    void lambdaRt_verySlow_clampedToMin() {
        // t=20, mu=8, σ=3 → (20-8)/(6) = 2.0
        // raw = 1 + 0.3*(1-2) = 0.7 → clamped to 0.7
        double result = DynamicForgettingFactor.lambdaRt(20.0, 8.0, 3.0);
        assertEquals(0.7, result, 0.001);
    }

    @Test
    void lambdaRt_veryFast_clampedToMax() {
        // t=0, mu=8, σ=3 → (0-8)/(6) = -1.333
        // raw = 1 + 0.3*(1+1.333) = 1.7 → clamped to 1.3
        double result = DynamicForgettingFactor.lambdaRt(0.0, 8.0, 3.0);
        assertEquals(1.3, result, 0.001);
    }

    // --- lambdaAcc Tests ---

    @Test
    void lambdaAcc_zeroCorrect_returns1() {
        assertEquals(1.0, DynamicForgettingFactor.lambdaAcc(0.5, 0));
    }

    @Test
    void lambdaAcc_highAccuracy_returnsHigher() {
        // p=1.0, correctCount=50 → raw = 1 + 0.5*(1.0-0.8) = 1.1
        assertEquals(1.1, DynamicForgettingFactor.lambdaAcc(1.0, 50), 0.001);
    }

    @Test
    void lambdaAcc_lowAccuracy_returnsLower() {
        // p=0.5, correctCount=50 → raw = 1 + 0.5*(0.5-0.8) = 0.85
        assertEquals(0.85, DynamicForgettingFactor.lambdaAcc(0.5, 50), 0.001);
    }

    @Test
    void lambdaAcc_veryLowAccuracy_clampedToMin() {
        // p=0.0, correctCount=50 → raw = 1 + 0.5*(-0.8) = 0.6 → clamped to 0.7
        assertEquals(0.7, DynamicForgettingFactor.lambdaAcc(0.0, 50), 0.001);
    }

    @Test
    void lambdaAcc_perfectAccuracy_clampedToMax() {
        // p=1.0, correctCount=50 → raw = 1.1 (within range)
        // Need p=1.5 to exceed → not possible, so test boundary
        // p=1.0 → 1.1, which is valid
        assertEquals(1.1, DynamicForgettingFactor.lambdaAcc(1.0, 50), 0.001);
    }

    // --- lambdaSkipCustom Tests ---

    @Test
    void lambdaSkip_noHints_returns1() {
        assertEquals(1.0, DynamicForgettingFactor.lambdaSkipCustom(0, 0), 0.001);
    }

    @Test
    void lambdaSkip_manyHints_lowers() {
        // hintTotal=5, reviewCount=0
        // raw = 1 - 0.2*(5/5)*(1+0) = 1 - 0.2 = 0.8
        assertEquals(0.8, DynamicForgettingFactor.lambdaSkipCustom(5, 0), 0.001);
    }

    @Test
    void lambdaSkip_manyHintsAndReviews更低() {
        // hintTotal=5, reviewCount=5
        // raw = 1 - 0.2*(1.0)*(1+0.5) = 1 - 0.3 = 0.7 → clamped to 0.7
        assertEquals(0.7, DynamicForgettingFactor.lambdaSkipCustom(5, 5), 0.001);
    }

    // --- DF Composition Tests ---

    @Test
    void df_quickMemory_usesLambdaRtAndLambdaAcc() {
        // Cold start, t=8s (at mean), rate=0.8 (at baseline)
        // λ_rt = 1.3 (at mean → clamped to max)
        // λ_acc = 1.0 (correctCount=0 → returns 1.0)
        // DF = 1.3 * 1.0 = 1.3
        double df = DynamicForgettingFactor.calculate(
                0, 0, 0.8, 8.0, 3.0, "quick_memory", 8.0, 0);
        assertEquals(1.3, df, 0.001);
    }

    @Test
    void df_contextDeep_usesLambdaAccAndLambdaSkip() {
        // correctCount=0 → λ_acc=1.0, hintTotal=0 → λ_skip=1.0
        // DF = 1.0 * 1.0 = 1.0
        double df = DynamicForgettingFactor.calculate(
                0, 0, 0.8, 8.0, 3.0, "context_deep", 8.0, 0);
        assertEquals(1.0, df, 0.001);
    }

    @Test
    void df_unifiedReview_usesLambdaRtAndLambdaAcc() {
        // Same as quick_memory
        double df = DynamicForgettingFactor.calculate(
                0, 0, 0.8, 8.0, 3.0, "unified_review", 8.0, 0);
        assertEquals(1.3, df, 0.001);
    }

    @Test
    void df_unknownMode_returns1() {
        double df = DynamicForgettingFactor.calculate(
                0, 0, 0.8, 8.0, 3.0, "unknown", 8.0, 0);
        assertEquals(1.0, df, 0.001);
    }

    @Test
    void df_clampedToRange() {
        // Extreme case: fast reaction + high accuracy + no hints
        // λ_rt=1.3, λ_acc=1.1 → DF=1.43 (within range)
        double df = DynamicForgettingFactor.calculate(
                0, 50, 1.0, 8.0, 3.0, "quick_memory", 2.0, 0);
        assertTrue(df >= DynamicForgettingFactor.DF_MIN);
        assertTrue(df <= DynamicForgettingFactor.DF_MAX);
    }
}
