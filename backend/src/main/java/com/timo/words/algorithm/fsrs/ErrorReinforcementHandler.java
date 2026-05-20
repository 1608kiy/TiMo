package com.timo.words.algorithm.fsrs;

import com.timo.words.algorithm.common.ClampUtil;
import com.timo.words.modules.study.entity.UserWordBind;

import java.time.LocalDateTime;

public final class ErrorReinforcementHandler {

    private ErrorReinforcementHandler() {}

    public static void apply(UserWordBind bind, String studyMode, double grade,
                             int[] stepScores, int hintTotal,
                             boolean spellingCorrect, boolean spellingAttempted) {

        boolean stubbornMarked = false;

        switch (studyMode) {
            case "quick_memory":
                stubbornMarked = applyQuickMemory(bind, grade);
                break;
            case "context_deep":
                stubbornMarked = applyContextDeep(bind, grade, stepScores, hintTotal);
                break;
            case "unified_review":
                stubbornMarked = applyUnifiedReview(bind, grade, spellingCorrect, spellingAttempted);
                break;
        }

        // Stubborn unmarking: grade >= 3.5 for 3 consecutive reviews in same mode
        // Skip if stubborn was just marked in this call
        boolean isStubborn = Boolean.TRUE.equals(bind.getIsStubborn());
        int consecutiveCorrect = bind.getConsecutiveCorrectSameMode() != null
                ? bind.getConsecutiveCorrectSameMode() : 0;
        int consecutiveErrors = bind.getConsecutiveErrors() != null
                ? bind.getConsecutiveErrors() : 0;

        if (isStubborn && !stubbornMarked && grade >= 3.5) {
            if (studyMode.equals(bind.getLastStudyMode())) {
                bind.setConsecutiveCorrectSameMode(consecutiveCorrect + 1);
            } else {
                bind.setConsecutiveCorrectSameMode(1);
            }
            bind.setLastStudyMode(studyMode);

            if (bind.getConsecutiveCorrectSameMode() >= 3) {
                unmarkStubborn(bind);
            }
        } else if (grade >= 3.5) {
            if (studyMode.equals(bind.getLastStudyMode())) {
                bind.setConsecutiveCorrectSameMode(consecutiveCorrect + 1);
            } else {
                bind.setConsecutiveCorrectSameMode(1);
            }
            bind.setLastStudyMode(studyMode);
        } else {
            // Non-consecutive good review — reset the consecutive counter
            bind.setConsecutiveCorrectSameMode(0);
        }

        // Manage consecutiveErrors (grade <= 2.0 counts as error)
        if (grade <= 2.0) {
            bind.setConsecutiveErrors(consecutiveErrors + 1);
        } else if (grade >= 3.5) {
            bind.setConsecutiveErrors(0);
        }

        // Clamp values to valid ranges
        bind.setStability(ClampUtil.clamp(bind.getStability(), Scheduler.STABILITY_MIN, Scheduler.STABILITY_MAX));
        bind.setDifficulty(ClampUtil.clamp(bind.getDifficulty(), Scheduler.DIFFICULTY_MIN, Scheduler.DIFFICULTY_MAX));
    }

    private static boolean applyQuickMemory(UserWordBind bind, double grade) {
        if (grade <= 2.0) {
            if (bind.getConsecutiveErrors() != null && bind.getConsecutiveErrors() >= 1) {
                // Second consecutive failure: harsh penalty + mark stubborn
                bind.setStability(bind.getStability() * 0.8);
                bind.setDifficulty(bind.getDifficulty() + 0.1);
                markStubborn(bind);
                return true;
            } else {
                // First failure: mild penalty
                bind.setStability(bind.getStability() * 0.9);
                bind.setDifficulty(bind.getDifficulty() + 0.05);
            }
        }
        return false;
    }

    private static boolean applyContextDeep(UserWordBind bind, double grade,
                                            int[] stepScores, int hintTotal) {
        boolean marked = false;

        // Severe spelling difficulty (s5 <= 1)
        if (stepScores != null && stepScores.length >= 4 && stepScores[3] <= 1) {
            bind.setStability(bind.getStability() * 0.8);
            bind.setDifficulty(bind.getDifficulty() + 0.08);
        }

        // Heavy hint dependence (hintTotal >= 4)
        if (hintTotal >= 4) {
            bind.setStability(bind.getStability() * 0.85);
        }

        // Consecutive errors across groups
        if (grade < 2.0 && bind.getConsecutiveErrors() != null && bind.getConsecutiveErrors() >= 1) {
            markStubborn(bind);
            marked = true;
        }

        // Scenario 3: Immediate stubborn clear — conquering a stubborn word in
        // context_deep mode (grade >= 3.5) clears the stubborn mark right away,
        // since deep learning is specifically designed for stubborn word remediation.
        if (grade >= 3.5 && Boolean.TRUE.equals(bind.getIsStubborn())) {
            unmarkStubborn(bind);
        }

        return marked;
    }

    private static boolean applyUnifiedReview(UserWordBind bind, double grade,
                                              boolean spellingCorrect, boolean spellingAttempted) {
        if (grade < 2.0) {
            bind.setStability(bind.getStability() * 0.85);
            bind.setDifficulty(bind.getDifficulty() + 0.05);
        }

        if (spellingAttempted && !spellingCorrect) {
            bind.setStability(bind.getStability() * 0.8);
            bind.setDifficulty(bind.getDifficulty() + 0.08);
            markStubborn(bind);
            return true;
        }

        return false;
    }

    private static void markStubborn(UserWordBind bind) {
        bind.setIsStubborn(true);
        bind.setStubbornSince(LocalDateTime.now());
    }

    private static void unmarkStubborn(UserWordBind bind) {
        bind.setIsStubborn(false);
        bind.setStubbornSince(null);
        bind.setConsecutiveErrors(0);
        bind.setConsecutiveCorrectSameMode(0);
    }
}
